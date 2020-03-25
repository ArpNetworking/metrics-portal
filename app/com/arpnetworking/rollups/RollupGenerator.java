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
import akka.pattern.Patterns;
import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.Aggregator;
import com.arpnetworking.kairos.client.models.DataPoint;
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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigUtil;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor for generating rollup jobs for individual source metrics.
 * <p>
 * The {@code RollupGenerator} will periodically retrieve metrics eligible for rollup and enqueue jobs up
 * to the maximum number of backfill periods, if any are configured.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 *
 */
public class RollupGenerator extends AbstractActorWithTimers {

    /*
     * The Generator flow is as follows:
     *
     * While there is no work, we periodically request metric names from {@link MetricsDiscovery}.
     *
     * For each metric name received, perform the following:
     *
     *     1. Retrieve the tag names for this metric (TagNamesMessage)
     *     2. For each period size:
     *         2a. Query for the last data point given the maximum backfill and set of tags (LastDataPointMessage)
     *         2b. If this datapoint is in the past, generate a backfill job for the period
     *         furthest in the past, and enqueue it by sending to the RollupManager. (FinishRollupMessage)
     *
     * In particular [2] leaves open the possibility of chunking rollups by tags as a future
     * optimization in order to break down the unit of work. At the moment we forward all tags
     * within a LastDataPointMessage, meaning that rollups operate on a metric as a whole.
     */

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
        _fetchBackoff = ConfigurationHelper.getFiniteDuration(configuration, "rollup.fetch.backoff");

        final ImmutableMap.Builder<RollupPeriod, Integer> maxBackFillByPeriod = ImmutableMap.builder();
        for (RollupPeriod period : RollupPeriod.values()) {
            final String periodName = period.name().toLowerCase(Locale.ENGLISH);
            final String key = ConfigUtil.joinPath("rollup", "maxBackFill", "periods", periodName);
            if (configuration.hasPath(key)) {
                maxBackFillByPeriod.put(period, configuration.getInt(key));
            }
        }
        _maxBackFillByPeriod = maxBackFillByPeriod.build();
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
        Patterns.pipe(_kairosDbClient.queryMetricTags(
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
            return;
        }
        _metrics.recordCounter("rollup/generator/tag_names_message/success", 1);
        _periodsInFlight = Lists.newArrayList(RollupPeriod.values());
        final String metricName = message.getMetricName();
        final long startTime = System.nanoTime();
        for (final RollupPeriod period : RollupPeriod.values()) {
            final int backfillPeriods = _maxBackFillByPeriod.getOrDefault(period, 0);
            final String rollupMetricName = metricName + period.getSuffix();
            // Get the source of the rollup data, which is either the next smallest rollup metric or
            // the original metric, if this is the smallest possible rollup.
            final String sourceMetricName = period.previous()
                    .map(RollupPeriod::getSuffix)
                    .map(suffix -> metricName + suffix)
                    .orElse(metricName);
            if (backfillPeriods > 0) {
                Patterns.pipe(
                    fetchLastDataPoint(sourceMetricName, rollupMetricName, period, backfillPeriods)
                        .handle((response, failure) -> {
                            final String baseMetricName = "rollup/generator/last_data_point_"
                                    + period.name().toLowerCase(Locale.getDefault());
                            _metrics.recordCounter(baseMetricName + "/success", failure == null ? 1 : 0);
                            _metrics.recordTimer(
                                 baseMetricName + "/latency",
                                 System.nanoTime() - startTime,
                                Optional.of(Units.NANOSECOND)
                            );
                            return buildLastDataPointResponse(
                                sourceMetricName,
                                rollupMetricName,
                                period,
                                message.getTagNames(),
                                response,
                                failure
                            );
                        }),
                    getContext().dispatcher()
                ).to(getSelf());
            }
        }
    }

    private void handleLastDataPointMessage(final LastDataPointMessage message) {
        final String sourceMetricName = message.getSourceMetricName();
        final String rollupMetricName = message.getRollupMetricName();
        final RollupPeriod period = message.getPeriod();

        _metrics.recordCounter("rollup/generator/last_data_point_message/received", 1);
        if (message.isFailure()) {
            final Throwable throwable = message.getFailure().orElse(new RuntimeException("Received Failure"));

            _metrics.recordCounter("rollup/generator/last_data_point_message/success", 0);
            LOGGER.warn()
                    .setMessage("Failed to get last data point for metric.")
                    .addData("sourceMetricName", sourceMetricName)
                    .addData("rollupMetricName", rollupMetricName)
                    .setThrowable(throwable)
                    .log();

            getSelf().tell(
                    new FinishRollupMessage.Builder()
                            .setMetricName(sourceMetricName)
                            .setPeriod(period)
                            .setFailure(throwable)
                            .build(),
                    ActorRef.noSender());
        } else {
            _metrics.recordCounter("rollup/generator/last_data_point_message/success", 1);

            // Example:
            //
            // Consider a minutely metric that has just hit 00:00 UTC
            //
            // Hourly pulls from the minutely, and sees that the most recently closed period
            // is before the most recent datapoint in minutely, and so we can roll up.
            //
            // Daily pulls from hourly, but the latest hourly datapoint is at 23:00 < 00:00, the end
            // of the most recent period. Therefore we cannot roll-up just yet.
            //
            //                 22:00         23:00         00:00      startOfLastEligiblePeriod
            //                   |             |             |
            // minutely  m m m m m m m m m m m m m m m m m m m                  N/A
            // hourly            H             H             |                 23:00
            // daily             |             |             |                 00:00 2 days ago

            final Instant lastRollupDataPoint = message.getRollupLastDataPointTime().orElse(Instant.EPOCH);

            final Instant startOfLastEligiblePeriod =
                message.getSourceLastDataPointTime()
                        .map(period::recentStartTime)
                        .orElse(Instant.EPOCH);

            // If the most recent period aligned start time is after the most recent datapoint then
            // we need to run the rollup, otherwise we can skip this and just send a finish message.
            if (startOfLastEligiblePeriod.isAfter(lastRollupDataPoint)) {
                final int maxBackFillPeriods = _maxBackFillByPeriod.getOrDefault(period, 0);

                final Instant oldestBackfillPoint = period.recentEndTime(_clock.instant())
                        .minus(period.periodCountToDuration(maxBackFillPeriods));

                // We either want to start at the oldest backfill point or the start of the period
                // after the last datapoint since it contains data for the period that follows it.
                Instant rollupPeriodStart = lastRollupDataPoint.isBefore(oldestBackfillPoint)
                        ? oldestBackfillPoint : period.recentEndTime(lastRollupDataPoint).plus(period.periodCountToDuration(1));

                final Queue<Instant> startTimes = new LinkedList<>();

                // We need to rollup every period up to and including the most recent eligible period.
                while (rollupPeriodStart.isBefore(startOfLastEligiblePeriod) || rollupPeriodStart.equals(startOfLastEligiblePeriod)) {
                    startTimes.add(rollupPeriodStart);
                    rollupPeriodStart = rollupPeriodStart.plus(period.periodCountToDuration(1));
                }

                final RollupDefinition.Builder rollupDefBuilder = new RollupDefinition.Builder()
                        .setSourceMetricName(message.getSourceMetricName())
                        .setDestinationMetricName(rollupMetricName)
                        .setPeriod(period)
                        .setGroupByTags(message.getTags());

                while (!startTimes.isEmpty()) {
                    final Instant startTime = startTimes.poll();
                    rollupDefBuilder.setStartTime(startTime)
                            .setEndTime(startTime.plus(period.periodCountToDuration(1)).minusMillis(1));
                    _rollupManagerPool.tell(rollupDefBuilder.build(), self());
                }
            }

            getSelf().tell(
                    new FinishRollupMessage.Builder()
                            .setMetricName(message.getSourceMetricName())
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
            final String sourceMetricName,
            final String rollupMetricName,
            final RollupPeriod period,
            final ImmutableSet<String> tags,
            final MetricsQueryResponse response,
            @Nullable final Throwable failure) {
        final LastDataPointMessage.Builder builder = new LastDataPointMessage.Builder()
                .setSourceMetricName(sourceMetricName)
                .setRollupMetricName(rollupMetricName)
                .setPeriod(period)
                .setTags(tags);
        if (failure != null) {
            return builder.setFailure(failure).build();
        }
        final Map<String, MetricsQueryResponse.QueryResult> queryResults =
                response.getQueries()
                        .stream()
                        .flatMap(query -> query.getResults().stream())
                        .collect(ImmutableMap.toImmutableMap(
                                MetricsQueryResponse.QueryResult::getName,
                                Function.identity()
                        ));

        // Query results should *only* contain the source and destination metric.
        if (queryResults.size() != 2 || !queryResults.containsKey(sourceMetricName) || !queryResults.containsKey(rollupMetricName)) {
            return builder.setFailure(new Exception("Unexpected query results.")).build();
        }

        // Set source time
        Optional.ofNullable(queryResults.get(sourceMetricName))
            .flatMap(qr -> qr.getValues().stream().findFirst())
            .map(DataPoint::getTime)
            .ifPresent(builder::setSourceLastDataPointTime);

        // Set rollup time
        Optional.ofNullable(queryResults.get(rollupMetricName))
                .flatMap(qr -> qr.getValues().stream().findFirst())
                .map(DataPoint::getTime)
                .ifPresent(builder::setRollupLastDataPointTime);

        return builder.build();
    }

    // Fetch the last data point of the rollup and its source to determine how far behind we are.
    private CompletionStage<MetricsQueryResponse> fetchLastDataPoint(
            final String sourceMetricName,
            final String rollupMetricName,
            final RollupPeriod period,
            final int backfillPeriods
    ) {
        final Metric.Builder metricBuilder = new Metric.Builder()
                .setAggregators(ImmutableList.of(
                        new Aggregator.Builder()
                                .setName("count")
                                .build())
                )
                .setLimit(1)
                .setOrder(Metric.Order.DESC);

        return _kairosDbClient.queryMetrics(
                new MetricsQuery.Builder()
                        .setStartTime(period.recentEndTime(_clock.instant()).minus(period.periodCountToDuration(backfillPeriods)))
                        .setEndTime(period.recentEndTime(_clock.instant()))
                        .setMetrics(ImmutableList.of(
                             metricBuilder.setName(sourceMetricName).build(),
                             metricBuilder.setName(rollupMetricName).build()
                        )).build());
    }

    private final ActorRef _metricsDiscovery;
    private final ActorRef _rollupManagerPool;
    private final KairosDbClient _kairosDbClient;
    private final Map<RollupPeriod, Integer> _maxBackFillByPeriod;
    private final FiniteDuration _fetchBackoff;
    private final Clock _clock;
    private final PeriodicMetrics _metrics;
    private List<RollupPeriod> _periodsInFlight = Collections.emptyList();

    static final Object FETCH_METRIC = new Object();
    private static final Logger LOGGER = LoggerFactory.getLogger(RollupGenerator.class);
}
