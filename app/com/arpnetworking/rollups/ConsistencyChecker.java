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
import akka.actor.Props;
import akka.actor.Status;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
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
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.steno.LogBuilder;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;
import net.sf.oval.constraint.ValidateWithMethod;

import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Actor that compares rollup datapoints to their source material, and logs any discrepancies.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class ConsistencyChecker extends AbstractActorWithTimers {

    private final KairosDbClient _kairosDbClient;
    private final MetricsFactory _metricsFactory;
    private final PeriodicMetrics _periodicMetrics;
    private final int _bufferSize;
    private final LinkedHashSet<Task> _queue;
    private int _nAvailableRequests;
    private final AtomicInteger _maxRecentBufferSize;

    @Override
    public Receive createReceive() {
        return new ReceiveBuilder()
                .matchEquals(TICK, msg -> tick())
                .matchEquals(REQUEST_FINISHED, msg -> {
                    _nAvailableRequests += 1;
                    tick();
                })
                .match(Task.class, task -> {
                    if (_queue.size() < _bufferSize) {
                        _queue.add(task);
                        getSender().tell(new Status.Success(task), getSelf());
                        _maxRecentBufferSize.accumulateAndGet(_queue.size(), Math::max);
                        recordCounter("submit/success", 1);
                        tick();
                    } else {
                        getSender().tell(new Status.Failure(BufferFull.getInstance()), getSelf());
                        recordCounter("submit/success", 0);
                    }
                })
                .match(SampleCounts.class, this::sampleCountsReceived)
                .build();
    }

    /**
     * Creates a {@link Props} for this actor.
     *
     * @param kairosDbClient kairosdb client
     * @param metricsFactory metrics factory
     * @param periodicMetrics periodic metrics
     * @param maxConcurrentRequests maximum number of queries that can be outstanding to KairosDB at a time
     * @param bufferSize maximum number of items to allow in the queue
     * @return A new Props.
     */
    public static Props props(
            final KairosDbClient kairosDbClient,
            final MetricsFactory metricsFactory,
            final PeriodicMetrics periodicMetrics,
            final int maxConcurrentRequests,
            final int bufferSize
    ) {
        return Props.create(ConsistencyChecker.class, kairosDbClient, metricsFactory, periodicMetrics, maxConcurrentRequests, bufferSize);
    }

    private ConsistencyChecker(
            final KairosDbClient kairosDbClient,
            final MetricsFactory metricsFactory,
            final PeriodicMetrics periodicMetrics,
            final int maxConcurrentRequests,
            final int bufferSize
    ) {
        _kairosDbClient = kairosDbClient;
        _metricsFactory = metricsFactory;
        _periodicMetrics = periodicMetrics;
        _bufferSize = bufferSize;
        _queue = new LinkedHashSet<>();
        _nAvailableRequests = maxConcurrentRequests;
        _maxRecentBufferSize = new AtomicInteger(0);

        _periodicMetrics.registerPolledMetric(metrics -> {
            metrics.recordGauge("rollup/consistency_checker/buffer_size", _maxRecentBufferSize.getAndSet(_queue.size()));
        });
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        getSelf().tell(TICK, getSelf());
        getTimers().startPeriodicTimer("PERIODIC_TICK", TICK, TICK_INTERVAL);
    }

    private Task dequeueWork() {
        @Nullable final Task result = Iterables.getFirst(_queue, null);
        if (result == null) {
            throw new IllegalStateException("queue is empty");
        }
        _queue.remove(result);
        return result;
    }

    private void recordCounter(final String metricName, final long value) {
        _periodicMetrics.recordCounter("rollup/consistency_checker/" + metricName, value);

    }

    private void tick() {
        recordCounter("tick", 1);
        while (_nAvailableRequests > 0 && !_queue.isEmpty()) {
            startRequest(dequeueWork());
        }
    }

    private void startRequest(final Task task) {
        recordCounter("request/start", 1);
        _nAvailableRequests -= 1;
        Patterns.pipe(
                _kairosDbClient.queryMetrics(buildCountComparisonQuery(task))
                        .whenComplete((response, error) -> {
                            getSelf().tell(REQUEST_FINISHED, getSelf());
                            recordCounter("request/finish_success", error == null ? 1 : 0);
                        })
                        .thenApply(response -> {
                            boolean parseFailure = false;
                            try {
                                return ConsistencyChecker.parseSampleCounts(task, response);
                            } catch (final MalformedSampleCountResponse err) {
                                parseFailure = true;
                                throw new CompletionException(err);
                            } finally {
                                recordCounter("parse_failure", parseFailure ? 1 : 0);
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
                        }),
                getContext().getDispatcher()
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

            final boolean tooManyRollupSamples = nOriginalSamples < nRollupSamples;
            recordCounter("too_many_rollup_samples", tooManyRollupSamples ? 1 : 0);
            if (tooManyRollupSamples) {
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
    /* package private */ static final Object TICK = new Object();
    private static final Object REQUEST_FINISHED = new Object();
    private static final Duration TICK_INTERVAL = Duration.ofMinutes(1);

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
        private final ImmutableMap<String, String> _filterTags;
        private final Trigger _trigger;

        /**
         * Why the {@link Task} was created. Used for metrics.
         */
        public enum Trigger {
            /**
             * Something/somebody requested this task as a one-off.
             */
            ON_DEMAND,
            /**
             * Some rollup data finished being written successfully.
             */
            WRITE_COMPLETED,
            // QUERIED,  // TODO(spencerpearson, OBS-1175)
        }

        private Task(final Builder builder) {
            _sourceMetricName = builder._sourceMetricName;
            _rollupMetricName = builder._rollupMetricName;
            _period = builder._period;
            _startTime = builder._startTime;
            _filterTags = builder._filterTags;
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

        public ImmutableMap<String, String> getFilterTags() {
            return _filterTags;
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
                    && _filterTags.equals(task._filterTags)
                    && _trigger == task._trigger;
        }

        @Override
        public int hashCode() {
            return Objects.hash(_sourceMetricName, _rollupMetricName, _period, _startTime, _filterTags, _trigger);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("_sourceMetricName", _sourceMetricName)
                    .add("_rollupMetricName", _rollupMetricName)
                    .add("_period", _period)
                    .add("_startTime", _startTime)
                    .add("_filterTags", _filterTags)
                    .add("_trigger", _trigger)
                    .toString();
        }

        /**
         * Builder for {@link Task}.
         */
        public static final class Builder extends ThreadLocalBuilder<Task> {
            @NotNull
            @NotEmpty
            private String _sourceMetricName;
            @NotNull
            @NotEmpty
            private String _rollupMetricName;
            @NotNull
            private RollupPeriod _period;
            @NotNull
            private ImmutableMap<String, String> _filterTags = ImmutableMap.of();
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

            @Override
            protected void reset() {
                _sourceMetricName = null;
                _rollupMetricName = null;
                _period = null;
                _filterTags = ImmutableMap.of();
                _startTime = null;
                _trigger = null;
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
             * Sets the {@code _filterTags} and returns a reference to this Builder so that the methods can be chained together.
             *
             * @param value the {@code _filterTags} to set
             * @return a reference to this Builder
             */
            public Builder setFilterTags(final ImmutableMap<String, String> value) {
                _filterTags = value;
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
     * Indicates that KairosDB returned "successfully" but the data was incomprehensible.
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

    /**
     * Indicates that a submitted {@link Task} was rejected because the internal task-buffer is full.
     */
    public static final class BufferFull extends Exception {
        private static final long serialVersionUID = 7840529083811798202L;
        private static final BufferFull INSTANCE = new BufferFull();

        /**
         * Get the singleton instance. (Other instances might be created by deserialization, though.)
         * @return the singleton instance
         */
        public static BufferFull getInstance() {
            return INSTANCE;
        }

        private BufferFull() {
            super("buffer is full");
        }
    }


}
