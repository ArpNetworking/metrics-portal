/*
 * Copyright 2019 Dropbox Inc.
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

import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.Aggregator;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.Sampling;
import com.arpnetworking.logback.annotations.Loggable;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import net.sf.oval.constraint.NotNull;
import org.apache.pekko.actor.AbstractActorWithTimers;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.pattern.Patterns;
import scala.concurrent.duration.FiniteDuration;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Actor that fetches RollupDefinitions from a RollupManager and performs the specified
 * Rollup.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public class RollupExecutor extends AbstractActorWithTimers {
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(RollupDefinition.class, this::executeRollup)
                .matchEquals(FETCH_ROLLUP, work -> this.fetchRollup())
                .match(NoMoreRollups.class, this::scheduleFetch)
                .match(FinishRollupMessage.class, this::handleFinishRollup)
                .build();
    }

    @Override
    public void preStart() {
        getSelf().tell(FETCH_ROLLUP, ActorRef.noSender());
    }

    private void executeRollup(final RollupDefinition rollupDefinition) {
        final long startTime = System.nanoTime();
        Patterns.pipe(
                performRollup(rollupDefinition)
                        .handle((response, failure) -> {
                            if (failure != null) {
                                LOGGER.warn()
                                        .setMessage("Failed to execute rollup query.")
                                        .addData("rollupMetricName", rollupDefinition.getDestinationMetricName())
                                        .addData("periodStartTime", rollupDefinition.getStartTime())
                                        .setThrowable(failure)
                                        .log();
                            }
                            final String baseMetricName = "rollup/executor/perform_rollup_"
                                    + rollupDefinition.getPeriod().name().toLowerCase(Locale.getDefault());
                            _metrics.recordCounter(baseMetricName + "/success", failure == null ? 1 : 0);
                            _metrics.recordTimer(
                                    baseMetricName + "/latency",
                                    System.nanoTime() - startTime,
                                    Optional.of(TimeUnit.NANOSECONDS));
                            return ThreadLocalBuilder.build(FinishRollupMessage.Builder.class, b -> b
                                    .setRollupDefinition(rollupDefinition)
                                    .setFailure(failure)
                            );
                        }), getContext().dispatcher())
                .to(getSelf());
    }


    private CompletionStage<MetricsQueryResponse> performRollup(final RollupDefinition rollupDefinition) {
        final MetricsQuery queryBuilder = buildQueryRollup(rollupDefinition, _ttlSeconds);
        return _kairosDbClient.queryMetrics(queryBuilder);
    }

    /* package private */ static MetricsQuery buildQueryRollup(final RollupDefinition rollupDefinition, final long ttlSeconds) {
        final String rollupMetricName = rollupDefinition.getDestinationMetricName();
        final RollupPeriod period = rollupDefinition.getPeriod();

        final Metric metric = ThreadLocalBuilder.build(Metric.Builder.class, metricBuilder -> {
            metricBuilder.setName(rollupDefinition.getSourceMetricName());
            metricBuilder.setTags(rollupDefinition.getFilterTags().asMultimap());

            if (!rollupDefinition.getAllMetricTags().isEmpty()) {
                metricBuilder.setGroupBy(ImmutableList.of(
                        ThreadLocalBuilder.build(MetricsQuery.QueryTagGroupBy.Builder.class, builder -> builder
                                .setTags(rollupDefinition.getAllMetricTags().keySet())
                                .build()
                        )
                ));
            }
            metricBuilder.setAggregators(ImmutableList.of(
                    new Aggregator.Builder()
                            .setName("merge")
                            .setSampling(new Sampling.Builder()
                                    .setValue(1)
                                    .setUnit(period.getSamplingUnit())
                                    .build())
                            .setAlignSampling(true)
                            .setAlignStartTime(true)
                            .build(),
                    new Aggregator.Builder()
                            .setName("save_as")
                            .setOtherArgs(ImmutableMap.of(
                                    "metric_name", rollupMetricName,
                                    "add_saved_from", false,
                                    "ttl", ttlSeconds
                            ))
                            .build(),
                    new Aggregator.Builder()
                            .setName("count")
                            .build()
            ));
        });

        return ThreadLocalBuilder.build(MetricsQuery.Builder.class, queryBuilder -> {
            queryBuilder.setStartTime(rollupDefinition.getStartTime());
            queryBuilder.setEndTime(rollupDefinition.getEndTime());

            queryBuilder.setMetrics(ImmutableList.of(metric));
        });
    }

    private void fetchRollup() {
        _metrics.recordCounter("rollup/executor/fetch_rollup_message/received", 1);
        _rollupManager.tell(RollupFetch.getInstance(), getSelf());
    }

    private void handleFinishRollup(final FinishRollupMessage message) {
        _metrics.recordCounter("rollup/executor/finish_rollup_message/received", 1);
        _rollupManager.tell(message, getSelf());
        fetchRollup();
    }

    private void scheduleFetch(final NoMoreRollups message) {
        _metrics.recordCounter("rollup/executor/no_more", 1);
        timers().startSingleTimer(FETCH_TIMER, FETCH_ROLLUP, _pollInterval);
    }

    /**
     * {@link RollupExecutor} actor constructor.
     *
     * @param configuration play configuration
     * @param rollupManager actor ref to RollupManager actor
     * @param kairosDbClient kairosdb client
     * @param metrics periodic metrics instance
     */
    @Inject
    public RollupExecutor(
            final Config configuration,
            @Named("RollupManager") final ActorRef rollupManager,
            final KairosDbClient kairosDbClient,
            final PeriodicMetrics metrics) {
        _rollupManager = rollupManager;
        _kairosDbClient = kairosDbClient;
        _metrics = metrics;
        _pollInterval = ConfigurationHelper.getFiniteDuration(configuration, "rollup.executor.pollInterval");
        _ttlSeconds = ConfigurationHelper.getFiniteDuration(configuration, "rollup.ttl").toSeconds();
    }

    private final KairosDbClient _kairosDbClient;
    private final PeriodicMetrics _metrics;
    private final ActorRef _rollupManager;
    private final FiniteDuration _pollInterval;
    private static final String FETCH_TIMER = "rollupFetchTimer";
    static final Object FETCH_ROLLUP = new Object();
    private static final Logger LOGGER = LoggerFactory.getLogger(RollupExecutor.class);
    private final long _ttlSeconds;

    @Loggable
    static final class FinishRollupMessage extends FailableMessage {
        private static final long serialVersionUID = -5696789105734902279L;
        private final RollupDefinition _rollupDefinition;

        FinishRollupMessage(final Builder builder) {
            super(builder);
            this._rollupDefinition = builder._rollupDefinition;
        }

        public RollupDefinition getRollupDefinition() {
            return _rollupDefinition;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final FinishRollupMessage that = (FinishRollupMessage) o;
            return _rollupDefinition.equals(that._rollupDefinition)
                    && getFailure().equals(that.getFailure());
        }

        @Override
        public int hashCode() {
            return Objects.hash(_rollupDefinition, getFailure());
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("_rollupDefinition", _rollupDefinition)
                    .add("_failure", getFailure())
                    .toString();
        }

        /**
         * {@link FinishRollupMessage} builder static inner class.
         */
        public static final class Builder extends FailableMessage.Builder<Builder, FinishRollupMessage> {

            /**
             * Creates a builder for a FinishRollupMessage.
             */
            Builder() {
                super(FinishRollupMessage::new);
            }

            /**
             * Sets the {@code _metricName} and returns a reference to this Builder so that the methods can be chained together.
             *
             * @param value the {@code _metricName} to set
             * @return a reference to this Builder
             */
            public Builder setRollupDefinition(final RollupDefinition value) {
                _rollupDefinition = value;
                return this;
            }


            @Override
            protected void reset() {
                super.reset();
                _rollupDefinition = null;
            }

            @Override
            protected Builder self() {
                return this;
            }

            @NotNull
            private RollupDefinition _rollupDefinition;
        }
    }
}
