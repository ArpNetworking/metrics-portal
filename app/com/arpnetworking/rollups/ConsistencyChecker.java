/*
 * Copyright 2020 Dropbox Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arpnetworking.rollups;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Status;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.Aggregator;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.Sampling;
import com.arpnetworking.logback.annotations.Loggable;
import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.steno.LogBuilder;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;
import net.sf.oval.constraint.ValidateWithMethod;

import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * Actor that compares rollup datapoints to their source material, and logs any discrepancies.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class ConsistencyChecker extends AbstractActorWithTimers {

    private final KairosDbClient _kairosDbClient;
    private final MetricsFactory _metricsFactory;
    private final ActorRef _queue;

    @Override
    public Receive createReceive() {
        return new ReceiveBuilder()
                .matchEquals(WORK_REQUEST_BACKOFF_MSG, msg -> backoff())
                .matchEquals(WORK_REQUEST_BACKOFF_EXPIRED_MSG, msg -> this.requestWork())
                .match(Task.class, this::startTask)
                .match(SampleCounts.class, this::sampleCountsReceived)
                .build();
    }

    /**
     * {@link ConsistencyChecker} actor constructor.
     *
     * @param kairosDbClient kairosdb client
     * @param metricsFactory metrics factory
     * @param queue queue to request {@link Task}s from
     */
    @Inject
    public ConsistencyChecker(
            final KairosDbClient kairosDbClient,
            final MetricsFactory metricsFactory,
            @Named("RollupConsistencyCheckerQueue") final ActorRef queue
    ) {
        _kairosDbClient = kairosDbClient;
        _metricsFactory = metricsFactory;
        _queue = queue;
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        requestWork();
    }

    private void backoff() {
        getTimers().startSingleTimer("WORK_REQUEST_BACKOFF", WORK_REQUEST_BACKOFF_EXPIRED_MSG, REQUEST_WORK_BACKOFF);
    }

    private void requestWork() {
        final CompletionStage<Object> ask = Patterns.ask(_queue, CollectionActor.Poll.getInstance(), Duration.ofSeconds(10))
                .handle((response, failure) -> {
                    if (failure == null) {
                        if (response instanceof Task) {
                            return response;
                        } else {
                            LOGGER.error()
                                    .setMessage("unexpected response from queue")
                                    .addData("response", response)
                                    .log();
                            return WORK_REQUEST_BACKOFF_MSG;
                        }
                    } else if (failure instanceof CollectionActor.Empty) {
                        LOGGER.debug()
                                .setMessage("queue is empty; backing off")
                                .log();
                        return WORK_REQUEST_BACKOFF_MSG;
                    } else {
                        LOGGER.error()
                                .setMessage("communication with queue failed")
                                .setThrowable(failure)
                                .log();
                        return WORK_REQUEST_BACKOFF_MSG;
                    }
                });
        Patterns.pipe(ask, getContext().getDispatcher()).to(getSelf());
    }

    private void startTask(final Task task) {
        LOGGER.debug()
                .setMessage("starting task")
                .addData("task", task)
                .log();
        Patterns.pipe(
                _kairosDbClient.queryMetrics(buildCountComparisonQuery(task))
                        .thenApply(response -> {
                            try {
                                return ConsistencyChecker.parseSampleCounts(task, response);
                            } catch (final MalformedSampleCountResponse err) {
                                throw new CompletionException(err);
                            }
                        })
                        .whenComplete((sampleCounts, failure) -> {
                            if (failure != null) {
                                LOGGER.error()
                                        .setMessage("failed to fetch/parse response from KairosDB")
                                        .addData("task", task)
                                        .setThrowable(failure)
                                        .log();
                            }
                        })
                        .whenComplete((response, failure) -> requestWork()),
                getContext().dispatcher()
        ).to(getSelf());
    }

    private void sampleCountsReceived(final SampleCounts sampleCounts) {
        final Task task = sampleCounts.getTask();
        final Optional<Throwable> failure = sampleCounts.getFailure();

        try (Metrics metrics = _metricsFactory.create()) {
            metrics.addAnnotation("trigger", task.getTrigger().name());
            metrics.addAnnotation("period", task.getPeriod().name());

            metrics.incrementCounter("rollup/consistency_checker/query_successful", failure.isPresent() ? 0 : 1);

            if (failure.isPresent()) {
                LOGGER.warn()
                        .setMessage("failed to query Kairos for sample-counts")
                        .addData("task", task)
                        .setThrowable(failure.get())
                        .log();
                return;
            }

            final double nOriginalSamples = sampleCounts.getSourceSampleCount();
            final double nRollupSamples = sampleCounts.getRollupSampleCount();
            final double nSamplesDropped = nOriginalSamples - nRollupSamples;
            if (nOriginalSamples < nRollupSamples) {
                LOGGER.error()
                        .setMessage("somehow got more samples for rolled-up data than original data")
                        .addData("task", task)
                        .addData("sampleCounts", sampleCounts)
                        .log();
            } else if (nOriginalSamples > nRollupSamples) {
                final double fractionalDataLoss = nSamplesDropped / nOriginalSamples;
                metrics.setGauge("rollup/consistency_checker/fractional_data_loss", fractionalDataLoss);
                final LogBuilder logBuilder =
                        // This level-thresholding should probably be configurable.
                        fractionalDataLoss < 0.001 ? LOGGER.debug()
                                : fractionalDataLoss < 0.01 ? LOGGER.info()
                                : fractionalDataLoss < 0.1 ? LOGGER.warn()
                                : LOGGER.error();
                logBuilder.setMessage("data lost in rollup")
                        .addData("task", task)
                        .addData("sampleCounts", sampleCounts)
                        .log();
            } else {
                LOGGER.trace()
                        .setMessage("no data lost in rollup")
                        .addData("task", task)
                        .addData("sampleCounts", sampleCounts)
                        .log();
            }
        }
    }

    /* package private */ static SampleCounts parseSampleCounts(
            final Task task,
            final MetricsQueryResponse response
    ) throws MalformedSampleCountResponse {
        final Map<String, Long> countsByMetric = Maps.newHashMap();
        if (response.getQueries().size() != 2) {
            throw new MalformedSampleCountResponse("expected exactly 2 queries, got " + response.getQueries().size(), response);
        }
        for (final MetricsQueryResponse.Query query : response.getQueries()) {
            if (query.getResults().size() != 1) {
                throw new MalformedSampleCountResponse("expected exactly 1 result, got " + query.getResults().size(), response);
            }
            final MetricsQueryResponse.QueryResult queryResult = query.getResults().get(0);

            if (queryResult.getValues().isEmpty()) {
                countsByMetric.put(queryResult.getName(), 0L);
            } else if (queryResult.getValues().size() != 1) {
                throw new MalformedSampleCountResponse("expected 0 or 1 values, got " + queryResult.getValues().size(), response);
            } else {
                final Optional<Object> value = queryResult.getValues().get(0).getValue();
                if (!value.isPresent()) {
                    throw new MalformedSampleCountResponse("sample count has null value", response);
                }
                final long longValue;
                try {
                    longValue = Double.valueOf(Double.parseDouble(value.get().toString())).longValue();
                } catch (final NumberFormatException e) {
                    throw new MalformedSampleCountResponse(e, response);
                }
                countsByMetric.put(queryResult.getName(), longValue);
            }
        }
        @Nullable final Long sourceCount = countsByMetric.get(task.getSourceMetricName());
        @Nullable final Long rollupCount = countsByMetric.get(task.getRollupMetricName());
        if (sourceCount == null || rollupCount == null) {
            throw new MalformedSampleCountResponse(
                    String.format("expected keys %s and %s", task.getSourceMetricName(), task.getRollupMetricName()),
                    response
            );
        }
        return ThreadLocalBuilder.build(SampleCounts.Builder.class, b -> b
                .setTask(task)
                .setSourceSampleCount(sourceCount)
                .setRollupSampleCount(rollupCount)
        );
    }

    private MetricsQuery buildCountComparisonQuery(final Task task) {
        final Consumer<Metric.Builder> setCommonFields = builder -> builder
                .setAggregators(ImmutableList.of(
                        ThreadLocalBuilder.build(Aggregator.Builder.class, aggb -> aggb
                                .setName("count")
                                .setSampling(ThreadLocalBuilder.build(Sampling.Builder.class, sb -> sb
                                        .setUnit(task.getPeriod().getSamplingUnit()).setValue(1).build()
                                ))
                                .setAlignSampling(true)
                                .setAlignStartTime(true)
                        ))
                );

        return ThreadLocalBuilder.build(MetricsQuery.Builder.class, mqb -> mqb
                .setStartTime(task.getStartTime())
                .setEndTime(task.getStartTime().plus(task.getPeriod().periodCountToDuration(1)).minusMillis(1))
                .setMetrics(ImmutableList.of(
                        ThreadLocalBuilder.build(Metric.Builder.class, mb -> {
                            setCommonFields.accept(mb);
                            mb.setName(task.getSourceMetricName());
                        }),
                        ThreadLocalBuilder.build(Metric.Builder.class, mb -> {
                            setCommonFields.accept(mb);
                            mb.setName(task.getRollupMetricName());
                        })
                ))
        );
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsistencyChecker.class);
    private static final Object WORK_REQUEST_BACKOFF_MSG = new Object();
    private static final Object WORK_REQUEST_BACKOFF_EXPIRED_MSG = new Object();
    private static final Duration REQUEST_WORK_BACKOFF = Duration.ofMinutes(1);

    /**
     * Commands the {@link ConsistencyChecker} to compare a rollup-datapoint against the corresponding source-datapoints.
     */
    @Loggable
    public static final class Task implements Serializable {
        private static final long serialVersionUID = 2980603523747090602L;
        private final String _sourceMetricName;
        private final String _rollupMetricName;
        private final RollupPeriod _period;
        private final Instant _startTime;
        private final Trigger _trigger;

        /**
         * Why the {@link Task} was created. Used for metrics.
         */
        public enum Trigger {
            /**
             * Something/somebody requested this task as a one-off.
             */
            ON_DEMAND,
            // WRITE_COMPLETED,  // TODO(spencerpearson, OBS-1174)
            // QUERIED,  // TODO(spencerpearson, OBS-1175)
        }

        private Task(final Builder builder) {
            _sourceMetricName = builder._sourceMetricName;
            _rollupMetricName = builder._rollupMetricName;
            _period = builder._period;
            _startTime = builder._startTime;
            _trigger = builder._trigger;
        }

        public String getSourceMetricName() {
            return _sourceMetricName;
        }

        public String getRollupMetricName() {
            return _rollupMetricName;
        }

        public RollupPeriod getPeriod() {
            return _period;
        }

        public Instant getStartTime() {
            return _startTime;
        }

        public Trigger getTrigger() {
            return _trigger;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Task task = (Task) o;
            return _sourceMetricName.equals(task._sourceMetricName)
                    && _rollupMetricName.equals(task._rollupMetricName)
                    && _period == task._period
                    && _startTime.equals(task._startTime)
                    && _trigger == task._trigger;
        }

        @Override
        public int hashCode() {
            return Objects.hash(_sourceMetricName, _rollupMetricName, _period, _startTime, _trigger);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("_sourceMetricName", _sourceMetricName)
                    .add("_rollupMetricName", _rollupMetricName)
                    .add("_period", _period)
                    .add("_startTime", _startTime)
                    .add("_trigger", _trigger)
                    .toString();
        }

        /**
         * Builder for {@link Task}.
         */
        public static final class Builder extends OvalBuilder<Task> {
            @NotNull
            @NotEmpty
            private String _sourceMetricName;
            @NotNull
            @NotEmpty
            private String _rollupMetricName;
            @NotNull
            private RollupPeriod _period;
            @NotNull
            @ValidateWithMethod(methodName = "validateStartTime", parameterType = Instant.class)
            private Instant _startTime;
            @NotNull
            private Trigger _trigger;

            /**
             * Public constructor.
             */
            public Builder() {
                super(Task::new);
            }

            /**
             * Sets the {@code _sourceMetricName} and returns a reference to this Builder so that the methods can be chained together.
             *
             * @param value the {@code _sourceMetricName} to set
             * @return a reference to this Builder
             */
            public Builder setSourceMetricName(final String value) {
                _sourceMetricName = value;
                return this;
            }

            /**
             * Sets the {@code _rollupMetricName} and returns a reference to this Builder so that the methods can be chained together.
             *
             * @param value the {@code _rollupMetricName} to set
             * @return a reference to this Builder
             */
            public Builder setRollupMetricName(final String value) {
                _rollupMetricName = value;
                return this;
            }

            /**
             * Sets the {@code _period} and returns a reference to this Builder so that the methods can be chained together.
             *
             * @param value the {@code _period} to set
             * @return a reference to this Builder
             */
            public Builder setPeriod(final RollupPeriod value) {
                _period = value;
                return this;
            }

            /**
             * Sets the {@code _startTime} and returns a reference to this Builder so that the methods can be chained together.
             *
             * @param value the {@code _startTime} to set
             * @return a reference to this Builder
             */
            public Builder setStartTime(final Instant value) {
                _startTime = value;
                return this;
            }

            /**
             * Sets the {@code _trigger} and returns a reference to this Builder so that the methods can be chained together.
             *
             * @param value the {@code _trigger} to set
             * @return a reference to this Builder
             */
            public Builder setTrigger(final Trigger value) {
                _trigger = value;
                return this;
            }

            @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "invoked reflectively by @ValidateWithMethod")
            private boolean validateStartTime(final Instant startTime) {
                return startTime.equals(_period.recentEndTime(startTime));
            }
        }
    }

    /**
     * Message indicating that sample-counts for a rollup datapoint and its source material have been successfully queried.
     */
    @Loggable
    public static final class SampleCounts extends FailableMessage implements Serializable {
        private static final long serialVersionUID = 5564783719460633635L;
        private final Task _task;
        private final long _sourceSampleCount;
        private final long _rollupSampleCount;

        private SampleCounts(final Builder builder) {
            super(builder);
            _task = builder._task;
            _sourceSampleCount = builder._sourceSampleCount;
            _rollupSampleCount = builder._rollupSampleCount;
        }

        public Task getTask() {
            return _task;
        }

        public long getSourceSampleCount() {
            return _sourceSampleCount;
        }

        public long getRollupSampleCount() {
            return _rollupSampleCount;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final SampleCounts that = (SampleCounts) o;
            return _sourceSampleCount == that._sourceSampleCount
                    && _rollupSampleCount == that._rollupSampleCount
                    && _task.equals(that._task);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_task, _sourceSampleCount, _rollupSampleCount);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("_task", _task)
                    .add("_sourceSampleCount", _sourceSampleCount)
                    .add("_rollupSampleCount", _rollupSampleCount)
                    .toString();
        }

        /**
         * Builder for {@link SampleCounts}.
         */
        public static final class Builder extends FailableMessage.Builder<Builder, SampleCounts> {
            @NotNull
            private Task _task;
            @NotNull
            private Long _sourceSampleCount;
            @NotNull
            private Long _rollupSampleCount;

            /**
             * Public constructor.
             */
            public Builder() {
                super(SampleCounts::new);
            }

            @Override
            protected Builder self() {
                return this;
            }

            @Override
            protected void reset() {
                super.reset();
                _task = null;
                _sourceSampleCount = null;
                _rollupSampleCount = null;
            }

            /**
             * Sets the {@code _task} and returns a reference to this Builder so that the methods can be chained together.
             *
             * @param value the {@code _task} to set
             * @return a reference to this Builder
             */
            public Builder setTask(final Task value) {
                _task = value;
                return this;
            }

            /**
             * Sets the {@code _sourceSampleCount} and returns a reference to this Builder so that the methods can be chained together.
             *
             * @param value the {@code _sourceSampleCount} to set
             * @return a reference to this Builder
             */
            public Builder setSourceSampleCount(final long value) {
                _sourceSampleCount = value;
                return this;
            }

            /**
             * Sets the {@code _rollupSampleCount} and returns a reference to this Builder so that the methods can be chained together.
             *
             * @param value the {@code _rollupSampleCount} to set
             * @return a reference to this Builder
             */
            public Builder setRollupSampleCount(final long value) {
                _rollupSampleCount = value;
                return this;
            }
        }
    }

    /**
     *
     */
    @Loggable
    public static final class MalformedSampleCountResponse extends Exception {
        private static final ObjectMapper MAPPER = ObjectMapperFactory.getInstance();
        private static final long serialVersionUID = 752780265350084369L;

        // This "should" be a MetricsQueryResponse, but that class isn't Serializable.
        // ("Why isn't it?" Several of the data-models contain Optional values, and Optional isn't Serializable.)
        private final JsonNode _response;

        /**
         * Public constructor.
         *
         * @param cause the error
         * @param response the {@link MetricsQueryResponse} whose parsing failed
         */
        public MalformedSampleCountResponse(final Throwable cause, final MetricsQueryResponse response) {
            super(cause);
            _response = MAPPER.valueToTree(response);
        }

        /**
         * Public constructor.
         *
         * @param message the error message
         * @param response the {@link MetricsQueryResponse} whose parsing failed
         */
        public MalformedSampleCountResponse(final String message, final MetricsQueryResponse response) {
            super(message);
            _response = MAPPER.valueToTree(response);
        }

        /**
         * Get the {@link MetricsQueryResponse} that failed to parse.
         *
         * @return the response
         */
        public MetricsQueryResponse getResponse() {
            try {
                return MAPPER.treeToValue(_response, MetricsQueryResponse.class);
            } catch (final IOException err) {
                throw new RuntimeException("somehow failed to deserialize a serialized MetricsQueryResponse", err);
            }
        }
    }

}
