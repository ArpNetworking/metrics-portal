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
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.commons.builder.ThreadLocalBuilder;
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.inject.Inject;

/**
 * TODO(spencerpearson)
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 *
 */
public class ConsistencyChecker extends AbstractActorWithTimers {

    @Override
    public Receive createReceive() {
        return new ReceiveBuilder()
                .match(Task.class, this::enqueueTask)
                .match(SampleCounts.class, this::compareSampleCounts)
                .build();
    }

    /**
     * {@link ConsistencyChecker} actor constructor.
     *
     * @param kairosDbClient kairosdb client
     * @param metricsFactory metrics factory
     */
    @Inject
    public ConsistencyChecker(
            final KairosDbClient kairosDbClient,
            final MetricsFactory metricsFactory) {
        _kairosDbClient = kairosDbClient;
        _metricsFactory = metricsFactory;
    }

    private void enqueueTask(final Task task) {
        if (_taskBuffer.size() > 10) {  // TODO(spencerpearson): make configurable
            // TODO: log
            return;
        }
    }
    private void startTask(final Task task) {
        _currentlyQuerying.set(true);
        Patterns.pipe(
                _kairosDbClient.queryMetrics(buildCountComparisonQuery(task))
                        .whenComplete((response, failure) -> _currentlyQuerying.set(false))
                        .thenApply(response -> {
                            try {
                                return getSampleCounts(response);
                            } catch (final MalformedSampleCountResponse e) {
                                throw new CompletionException(e);
                            }
                        })
                        .handle((sampleCounts, failure) -> ThreadLocalBuilder.build(SampleCounts.Builder.class, builder -> {
                            builder.setTask(task);
                            if (failure != null) {
                                builder.setFailure(failure);
                            } else {
                                builder.setSourceSampleCount(sampleCounts.get(task.getSourceMetricName()));
                                builder.setRollupSampleCount(sampleCounts.get(task.getRollupMetricName()));
                            }
                        })),
                getContext().dispatcher()
        ).to(getSelf());
    }

    private void compareSampleCounts(final SampleCounts sampleCounts) {
        final Metrics metrics = _metricsFactory.create();
        final Task task = sampleCounts.getTask();
        metrics.addAnnotation("trigger", task.getTrigger().name());
        metrics.addAnnotation("period", task.getPeriod().name());

        // TODO(spencerpearson): it might be useful to see how behavior varies by cardinality, something like
        // metrics.addAnnotation("log_realized_cardinality", Long.toString((long) Math.floor(Math.log10(task.getRealizedCardinality()))));

        final Optional<Throwable> failure = sampleCounts.getFailure();
        metrics.incrementCounter("rollup/consistency_checker/query_successful", failure.isPresent() ? 0 : 1);

        if (failure.isPresent()) {
            LOGGER.warn()
                    .setMessage("failed to query Kairos for sample-counts")
                    .addData("task", task)
                    .setThrowable(failure.get())
                    .log();
        } else {
            final double nOriginalSamples = sampleCounts.getSourceSampleCount();
            final double nSamplesDropped = nOriginalSamples - sampleCounts.getRollupSampleCount();
            final double fractionalDataLoss = nSamplesDropped / nOriginalSamples;
            metrics.setGauge("rollup/consistency_checker/fractional_data_loss", fractionalDataLoss);
            final LogBuilder logBuilder = (
                    // TODO(spencerpearson): probably make this level-thresholding configurable?
                    fractionalDataLoss == 0 ? LOGGER.trace() :
                    fractionalDataLoss < 0.001 ? LOGGER.debug() :
                    fractionalDataLoss < 0.01 ? LOGGER.info() :
                    fractionalDataLoss < 0.1 ? LOGGER.warn() :
                    LOGGER.error()
            );
            logBuilder.setMessage((fractionalDataLoss == 0 ? "no " : "") + "data lost in rollup")
                    .addData("task", task)
                    .addData("nOriginalSamples", nOriginalSamples)
                    .addData("nSamplesDropped", nSamplesDropped)
                    .addData("fractionalDataLoss", fractionalDataLoss)
                    .log();
        }

        /* TODO(spencerpearson, OBS-1176): re-trigger re-execution of bad datapoints, something like

            if (discrepancyTooBig) {
                _rollupManagerPool.tell(
                    new RollupDefinition.Builder()
                            .setSourceMetricName(task.getSourceMetricName())
                            .setDestinationMetricName(task.getRollupMetricName())
                            .setStartTime(task.getStartTime())
                            .setPeriod(task.getPeriod())
                            .build()
                );
            }

         */
    }

    public static final class MalformedSampleCountResponse extends Exception {
        private final MetricsQueryResponse _response;

        public MalformedSampleCountResponse(final String message, final MetricsQueryResponse response) {
            super(message);
            _response = response;
        }

        public MetricsQueryResponse getResponse() {
            return _response;
        }
    }

    private Map<String, Long> getSampleCounts(final MetricsQueryResponse response) throws MalformedSampleCountResponse {
        final Map<String, Long> result = Maps.newHashMap();
        for (final MetricsQueryResponse.Query query : response.getQueries()) {
            if (query.getResults().size() != 1) {
                throw new MalformedSampleCountResponse("expected exactly 1 result, got " + query.getResults().size(), response);
            }
            final MetricsQueryResponse.QueryResult queryResult = query.getResults().get(0);
            if (queryResult.getValues().size() != 1) {
                throw new MalformedSampleCountResponse("expected exactly 1 value, got " + queryResult.getValues().size(), response);
            }
            final Optional<Object> value = queryResult.getValues().get(0).getValue();
            if (!value.isPresent()) {
                throw new MalformedSampleCountResponse("sample count has null value", response);
            }
            final Long longValue;
            try {
                longValue = (Long) value.get();
            } catch (final ClassCastException e) {
                throw new MalformedSampleCountResponse(e.getMessage(), response);
            }
            result.put(queryResult.getName(), longValue);
        }
        return result;
    }

    private MetricsQuery buildCountComparisonQuery(final Task task) {
        final Consumer<Metric.Builder> setCommonFields = builder -> builder
                .setAggregators(ImmutableList.of(
                        new Aggregator.Builder()
                                .setName("count")
                                .setSampling(new Sampling.Builder().setUnit(task.getPeriod().getSamplingUnit()).setValue(1).build())
                                .setAlignSampling(true)
                                .setAlignStartTime(true)
                                .build())
                );

        return new MetricsQuery.Builder()
                .setStartTime(task.getStartTime())
                .setEndTime(task.getStartTime().plus(task.getPeriod().periodCountToDuration(1)).minusMillis(1))
                .setMetrics(ImmutableList.of(
                        ThreadLocalBuilder.build(Metric.Builder.class, b -> {
                            setCommonFields.accept(b);
                            b.setName(task.getSourceMetricName());
                        }),
                        ThreadLocalBuilder.build(Metric.Builder.class, b -> {
                            setCommonFields.accept(b);
                            b.setName(task.getRollupMetricName());
                        })
                )).build();
    }

    private final KairosDbClient _kairosDbClient;
    private final MetricsFactory _metricsFactory;
    private final Queue<Task> _taskBuffer = new ArrayDeque<>();
    private final AtomicBoolean _currentlyQuerying = new AtomicBoolean(false);

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsistencyChecker.class);

    @Loggable
    public static final class Task implements Serializable {
        private static final long serialVersionUID = 2980603523747090602L;
        private final String _sourceMetricName;
        private final String _rollupMetricName;
        private final RollupPeriod _period;
        private final Instant _startTime;
        public enum Trigger {
            HUMAN_REQUESTED,
            // WRITE_COMPLETED,  // TODO(spencerpearson, OBS-1174)
            // QUERIED,  // TODO(spencerpearson, OBS-1175)
        }
        private final Trigger _trigger;

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
            private Instant _startTime;
            @NotNull
            private Trigger _trigger;

            /**
             * Creates a builder for a {@link Task}
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
        }
    }

    @Loggable
    public static final class SampleCounts extends FailableMessage implements Serializable {
        private final static long serialVersionUID = 5564783719460633635L;
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

        public static final class Builder extends FailableMessage.Builder<Builder, SampleCounts> {
            @NotNull
            private Task _task;
            @NotNull
            private Long _sourceSampleCount;
            @NotNull
            private Long _rollupSampleCount;

            /**
             * Creates a builder for a {@link SampleCounts}
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
}
