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

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.PatternsCS;
import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.Aggregator;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricTags;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.TagsQuery;
import com.arpnetworking.metrics.Units;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.typesafe.config.Config;
import scala.concurrent.duration.FiniteDuration;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * Actor for performing rollups for individual source metrics.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public class RollupGenerator extends AbstractActorWithTimers {
    @Override
    public Receive createReceive() {
        return new ReceiveBuilder()
                .matchEquals(FETCH_METRIC, this::requestMetricsFromDiscovery)
                .match(String.class, this::fetchMetricTags)
                .match(TagNamesMessage.class, this::handleTagNamesMessage)
                .match(LastDataPointMessage.class, this::handleLastDataPointMessage)
                .match(FinishRollupMessage.class, this::handleFinishRollupMessage)
                .match(NoMoreMetrics.class, this::handleNoMoreMetricsMessage)
                .build();
    }


    /**
     * RollupGenerator actor constructor.
     *
     * @param configuration play configuration
     * @param metricsDiscovery actor ref to metrics discovery actor
     * @param rollupManagerPool actor ref for rollup manager pool actor
     * @param kairosDbClient kairosdb client
     * @param clock clock to use for time calculations
     * @param metrics periodic metrics instance
     */
    @Inject
    public RollupGenerator(
            final Config configuration,
            @Named("RollupMetricsDiscovery") final ActorRef metricsDiscovery,
            @Named("RollupManagerPool") final ActorRef rollupManagerPool,
            final KairosDbClient kairosDbClient,
            final Clock clock,
            final PeriodicMetrics metrics) {
        _metricsDiscovery = metricsDiscovery;
        _rollupManagerPool = rollupManagerPool;
        _kairosDbClient = kairosDbClient;
        _clock = clock;
        _metrics = metrics;
        _maxBackFillPeriods = configuration.getInt("rollup.maxBackFill.periods");
        _fetchBackoff = ConfigurationHelper.getFiniteDuration(configuration, "rollup.fetch.backoff");
    }

    @Override
    public void preStart() {
        getSelf().tell(FETCH_METRIC, ActorRef.noSender());
    }


    private void requestMetricsFromDiscovery(final Object fetch) {
        _metrics.recordCounter("rollup/generator/metric_names/requested", 1);
        _metricsDiscovery.tell(MetricFetch.getInstance(), getSelf());
    }

    private void fetchMetricTags(final String metricName) {
        _metrics.recordCounter("rollup/generator/metric_names_message/received", 1);
        final long startTime = System.nanoTime();
        PatternsCS.pipe(_kairosDbClient.queryMetricTags(
                new TagsQuery.Builder()
                        .setStartTime(Instant.ofEpochMilli(0))
                        .setMetrics(ImmutableList.of(
                                new MetricTags.Builder()
                                        .setName(metricName)
                                        .build()
                        ))
                        .build()).handle((response, failure) -> {
                    final String baseMetricName = "rollup/generator/tag_names";
                    _metrics.recordCounter(baseMetricName + "/success", failure == null ? 1 : 0);
                    _metrics.recordTimer(
                            baseMetricName + "/latency",
                            System.nanoTime() - startTime,
                            Optional.of(Units.NANOSECOND));
                    if (failure != null) {
                        return new TagNamesMessage.Builder()
                                .setMetricName(metricName)
                                .setFailure(failure)
                                .build();
                    } else {
                        if (response.getQueries().isEmpty() || response.getQueries().get(0).getResults().isEmpty()) {
                            return new TagNamesMessage.Builder()
                                    .setMetricName(metricName)
                                    .setFailure(new Exception("Unexpected query result."))
                                    .build();
                        } else {
                            return new TagNamesMessage.Builder()
                                    .setMetricName(metricName)
                                    .setTagNames(response.getQueries().get(0).getResults().get(0).getTags().keySet())
                                    .build();
                        }
                    }
                }),
                getContext().dispatcher())
                .to(getSelf());

    }

    private void handleTagNamesMessage(final TagNamesMessage message) {
        _metrics.recordCounter("rollup/generator/tag_names_message/received", 1);
        if (message.isFailure()) {
            _metrics.recordCounter("rollup/generator/tag_names_message/success", 0);
            LOGGER.warn()
                    .setMessage("Failed to get tag names for metric.")
                    .addData("metricName", message.getMetricName())
                    .setThrowable(message.getFailure().get())
                    .log();

            // Get the next metric
            getSelf().tell(FETCH_METRIC, ActorRef.noSender());
        } else {
            _metrics.recordCounter("rollup/generator/tag_names_message/success", 1);
            _periodsInFlight = Lists.newArrayList(RollupPeriod.values());
            final String metricName = message.getMetricName();
            final long startTime = System.nanoTime();
            for (final RollupPeriod period : RollupPeriod.values()) {
                PatternsCS.pipe(
                        fetchLastDataPoint(metricName + period.getSuffix(), period)
                                .handle((response, failure) -> {
                                    final String baseMetricName = "rollup/generator/last_data_point_"
                                            + period.name().toLowerCase(Locale.getDefault());
                                    _metrics.recordCounter(baseMetricName + "/success", failure == null ? 1 : 0);
                                    _metrics.recordTimer(
                                            baseMetricName + "/latency",
                                            System.nanoTime() - startTime,
                                            Optional.of(Units.NANOSECOND));
                                    return buildLastDataPointResponse(metricName, period, message.getTagNames(), response, failure);
                                }), getContext().dispatcher())
                        .to(getSelf());
            }
        }
    }

    private void handleLastDataPointMessage(final LastDataPointMessage message) {
        _metrics.recordCounter("rollup/generator/last_data_point_message/received", 1);
        if (message.isFailure()) {
            _metrics.recordCounter("rollup/generator/last_data_point_message/success", 0);
            LOGGER.warn()
                    .setMessage("Failed to get last data point for metric.")
                    .addData("metricName", message.getMetricName())
                    .setThrowable(message.getFailure().get())
                    .log();

            getSelf().tell(
                    new FinishRollupMessage.Builder()
                            .setMetricName(message.getMetricName())
                            .setPeriod(message.getPeriod())
                            .setFailure(message.getFailure().orElse(new RuntimeException("Received Failure")))
                            .build(),
                    ActorRef.noSender());
        } else {
            _metrics.recordCounter("rollup/generator/last_data_point_message/success", 1);
            final Instant recentPeriodStartTime = message.getPeriod().recentStartTime(_clock.instant());

            // If the most recent period aligned start time is after the most recent datapoint then
            // we need to run the rollup, otherwise we can skip this and just send a finish message.
            if (recentPeriodStartTime.isAfter(message.getLastDataPointTime().orElse(Instant.EPOCH))) {
                final String rollupMetricName = message.getMetricName() + message.getPeriod().getSuffix();
                final RollupPeriod period = message.getPeriod();

                // The last datapoint contains data for the period that follows it
                final Instant lastDataPoint = message.getLastDataPointTime().orElse(Instant.EPOCH);
                final Instant oldestBackfillPoint = period.recentEndTime(
                        _clock.instant()).minus(period.periodCountToDuration(_maxBackFillPeriods));
                final Instant startOfRecentClosedPeriod = period.recentStartTime(_clock.instant());

                // We either want to start at the oldest backfill point or the start of the period after the last datapoint.
                Instant rollupPeriodStart = lastDataPoint.isBefore(oldestBackfillPoint)
                        ? oldestBackfillPoint : lastDataPoint.plus(period.periodCountToDuration(1));

                final Queue<Instant> startTimes = new LinkedList<>();

                // We need to rollup every period up to and including the most recent period.
                while (rollupPeriodStart.isBefore(startOfRecentClosedPeriod) || rollupPeriodStart.equals(startOfRecentClosedPeriod)) {
                    startTimes.add(rollupPeriodStart);
                    rollupPeriodStart = rollupPeriodStart.plus(period.periodCountToDuration(1));
                }

                final RollupDefinition.Builder rollupBuilder = new RollupDefinition.Builder()
                        .setSourceMetricName(message.getMetricName())
                        .setDestinationMetricName(rollupMetricName)
                        .setPeriod(period)
                        .setGroupByTags(message.getTags());

                while (!startTimes.isEmpty()) {
                    final Instant startTime = startTimes.poll();

                    rollupBuilder.setStartTime(startTime)
                            .setEndTime(startTime.plus(period.periodCountToDuration(1)).minusMillis(1));
                    _rollupManagerPool.tell(rollupBuilder.build(), self());
                }
            }

            getSelf().tell(
                    new FinishRollupMessage.Builder()
                            .setMetricName(message.getMetricName())
                            .setPeriod(message.getPeriod())
                            .build(),
                    ActorRef.noSender()
            );
        }
    }

    private void handleFinishRollupMessage(final FinishRollupMessage message) {
        _metrics.recordCounter("rollup/generator/finish_rollup_message/received", 1);
        _periodsInFlight.remove(message.getPeriod());
        if (_periodsInFlight.isEmpty()) {
            getSelf().tell(FETCH_METRIC, ActorRef.noSender());
        }
    }

    private void handleNoMoreMetricsMessage(final NoMoreMetrics message) {
        _metrics.recordCounter("rollup/generator/metric_names/no_more", 1);
        _metrics.recordGauge("rollup/generator/metric_names/next_refresh", message.getNextRefreshMillis());
        timers().startSingleTimer("sleepTimer", FETCH_METRIC, _fetchBackoff);
    }

    private LastDataPointMessage buildLastDataPointResponse(
            final String metricName,
            final RollupPeriod period,
            final ImmutableSet<String> tags,
            final MetricsQueryResponse response,
            @Nullable final Throwable failure) {
        final LastDataPointMessage.Builder builder = new LastDataPointMessage.Builder()
                .setMetricName(metricName)
                .setPeriod(period)
                .setTags(tags);
        if (failure != null) {
            builder.setFailure(failure);
        } else {
            if (response.getQueries().isEmpty()
                    || response.getQueries().get(0).getResults().isEmpty()) {
                builder.setFailure(new Exception("Unexpected query results."));
            } else if (response.getQueries().get(0).getResults().get(0).getValues().isEmpty()) {
                builder.setLastDataPointTime(null);
            } else {
                builder.setLastDataPointTime(response.getQueries().get(0).getResults().get(0).getValues().get(0).getTime());
            }
        }
        return builder.build();
    }

    private CompletionStage<MetricsQueryResponse> fetchLastDataPoint(final String metricName, final RollupPeriod period) {
        return _kairosDbClient.queryMetrics(
                new MetricsQuery.Builder()
                        .setStartTime(period.recentEndTime(_clock.instant()).minus(period.periodCountToDuration(_maxBackFillPeriods)))
                        .setEndTime(period.recentEndTime(_clock.instant()))
                        .setMetrics(ImmutableList.of(
                                new Metric.Builder()
                                        .setName(metricName)
                                        .setAggregators(ImmutableList.of(
                                                new Aggregator.Builder()
                                                        .setName("count")
                                                        .build())
                                        )
                                        .setLimit(1)
                                        .setOrder(Metric.Order.DESC)
                                        .build()
                        )).build());
    }

    private final ActorRef _metricsDiscovery;
    private final ActorRef _rollupManagerPool;
    private final KairosDbClient _kairosDbClient;
    private final int _maxBackFillPeriods;
    private final FiniteDuration _fetchBackoff;
    private final Clock _clock;
    private final PeriodicMetrics _metrics;
    private List<RollupPeriod> _periodsInFlight = Collections.emptyList();

    static final Object FETCH_METRIC = new Object();
    private static final Logger LOGGER = LoggerFactory.getLogger(RollupGenerator.class);
}
