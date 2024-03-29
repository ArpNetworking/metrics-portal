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
import com.arpnetworking.kairos.client.models.DataPoint;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricTags;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.TagsQuery;
import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.notcommons.tagger.Tagger;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Lists;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigUtil;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.pekko.actor.AbstractActorWithTimers;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.pattern.Patterns;
import scala.concurrent.duration.FiniteDuration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.annotation.Nullable;

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
     *         2a. Query for the last data point given the maximum backfill and set of tags (LastDataPointsMessage)
     *         2b. If this datapoint is in the past, generate a backfill job for the period
     *         furthest in the past, and enqueue it by sending to the RollupManager. (FinishRollupMessage)
     *
     * In particular [2] leaves open the possibility of chunking rollups by tags as a future
     * optimization in order to break down the unit of work. At the moment we forward all tags
     * within a LastDataPointsMessage, meaning that rollups operate on a metric as a whole.
     */

    @Override
    public Receive createReceive() {
        return new ReceiveBuilder()
                .matchEquals(FETCH_METRIC, this::requestMetricsFromDiscovery)
                .match(String.class, this::fetchMetricTags)
                .match(TagNamesMessage.class, this::handleTagNamesMessage)
                .match(LastDataPointsMessage.class, this::handleLastDataPointsMessage)
                .match(FinishRollupMessage.class, this::handleFinishRollupMessage)
                .match(NoMoreMetrics.class, this::handleNoMoreMetricsMessage)
                .build();
    }

    /**
     * {@link RollupGenerator} actor constructor.
     *
     * @param configuration play configuration
     * @param metricsDiscovery actor ref to metrics discovery actor
     * @param rollupManager actor ref for rollup manager actor
     * @param kairosDbClient kairosdb client
     * @param clock clock to use for time calculations
     * @param periodicMetrics periodic metrics instance
     * @param metricsFactory metrics factory instance for instrumentation
     * @param tagger tagger instance for instrumentation
     */
    @Inject
    // CHECKSTYLE.OFF: ParameterNumber
    public RollupGenerator(
            final Config configuration,
            @Named("RollupMetricsDiscovery") final ActorRef metricsDiscovery,
            @Named("RollupManager") final ActorRef rollupManager,
            final KairosDbClient kairosDbClient,
            final Clock clock,
            final PeriodicMetrics periodicMetrics,
            final MetricsFactory metricsFactory,
            @Named("RollupGeneratorTagger") final Tagger tagger
    ) {
        _tagLookbackPeriods = 1;
        _metricsDiscovery = metricsDiscovery;
        _rollupManager = rollupManager;
        _kairosDbClient = kairosDbClient;
        _clock = clock;
        _periodicMetrics = periodicMetrics;
        _metricsFactory = metricsFactory;
        _fetchBackoff = ConfigurationHelper.getFiniteDuration(configuration, "rollup.fetch.backoff");
        _tagger = tagger;

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
    // CHECKSTYLE.ON: ParameterNumber

    @Override
    public void preStart() {
        getSelf().tell(FETCH_METRIC, ActorRef.noSender());
    }

    private void requestMetricsFromDiscovery(final Object fetch) {
        _periodicMetrics.recordCounter("rollup/generator/metric_names/requested", 1);
        _metricsDiscovery.tell(MetricFetch.getInstance(), getSelf());
    }

    private void fetchMetricTags(final String metricName) {
        _periodicMetrics.recordCounter("rollup/generator/metric_names_message/received", 1);
        final long startTime = System.nanoTime();
        final long now = System.currentTimeMillis();
        final long beginningOfPeriod = now - (now % KAIROSDB_PERIOD_MILLIS);
        final long startPeriod = beginningOfPeriod - (_tagLookbackPeriods * KAIROSDB_PERIOD_MILLIS);
        Patterns.pipe(_kairosDbClient.queryMetricTags(
                new TagsQuery.Builder()
                        .setStartTime(Instant.ofEpochMilli(startPeriod))
                        .setMetrics(ImmutableList.of(
                                ThreadLocalBuilder.build(MetricTags.Builder.class, builder -> builder.setName(metricName))
                        ))
                        .build()).handle((response, failure) -> {
                    final String baseMetricName = "rollup/generator/tag_names";
                    _periodicMetrics.recordCounter(baseMetricName + "/success", failure == null ? 1 : 0);
                    _periodicMetrics.recordTimer(
                            baseMetricName + "/latency",
                            System.nanoTime() - startTime,
                            Optional.of(TimeUnit.NANOSECONDS));
                    if (failure != null) {
                        return ThreadLocalBuilder.build(TagNamesMessage.Builder.class, b -> b
                                .setMetricName(metricName)
                                .setFailure(failure)
                        );
                    } else {
                        if (response.getQueries().isEmpty() || response.getQueries().get(0).getResults().isEmpty()) {
                            return ThreadLocalBuilder.build(TagNamesMessage.Builder.class, b -> b
                                    .setMetricName(metricName)
                                    .setFailure(new UnexpectedQueryResponseException("Empty queries or query results", response))
                            );
                        } else {
                            return ThreadLocalBuilder.build(TagNamesMessage.Builder.class, b -> b
                                    .setMetricName(metricName)
                                    .setTags(response.getQueries().get(0).getResults().get(0).getTags())
                            );
                        }
                    }
                }),
                getContext().dispatcher())
                .to(getSelf());

    }

    private void handleTagNamesMessage(final TagNamesMessage message) {
        _periodicMetrics.recordCounter("rollup/generator/tag_names_message/received", 1);
        _periodicMetrics.recordCounter("rollup/generator/tag_names_message/success", message.isFailure() ? 0 : 1);
        if (message.isFailure()) {
            LOGGER.warn()
                    .setMessage("Failed to get tag names for metric.")
                    .addData("metricName", message.getMetricName())
                    .setThrowable(message.getFailure().get())
                    .log();

            // Get the next metric
            getSelf().tell(FETCH_METRIC, ActorRef.noSender());
            return;
        }
        _periodsInFlight = Lists.newArrayList(RollupPeriod.values());
        final String metricName = message.getMetricName();
        final long startTime = System.nanoTime();
        for (final RollupPeriod period : RollupPeriod.values()) {
            final int backfillPeriods = _maxBackFillByPeriod.getOrDefault(period, 0);
            if (backfillPeriods > 0) {
                final String sourceMetricName = getSourceMetricName(metricName, period);
                final String rollupMetricName = getDestinationMetricName(metricName, period);
                Patterns.pipe(
                    _kairosDbClient.queryMetrics(buildLastDataPointQuery(sourceMetricName, rollupMetricName, period, backfillPeriods))
                        .handle((response, failure) -> {
                            final String baseMetricName = "rollup/generator/last_data_point_"
                                    + period.name().toLowerCase(Locale.getDefault());
                            _periodicMetrics.recordCounter(baseMetricName + "/success", failure == null ? 1 : 0);
                            _periodicMetrics.recordTimer(
                                 baseMetricName + "/latency",
                                 System.nanoTime() - startTime,
                                Optional.of(TimeUnit.NANOSECONDS)
                            );
                            return ThreadLocalBuilder.build(LastDataPointsMessage.Builder.class, b -> buildLastDataPointResponse(
                                    b,
                                    sourceMetricName,
                                    rollupMetricName,
                                    period,
                                    message.getTags(),
                                    response,
                                    failure
                            ));
                        }),
                    getContext().dispatcher()
                ).to(getSelf());
            }
        }
    }

    private String getSourceMetricName(final String metricName, final RollupPeriod period) {
        // Walk backwards in decreasing period size to find the next smallest rollup metric that's enabled,
        // or the original metric if no smaller rollups are available.
        //
        // XXX(cbriones): This code assumes that all smaller periods evenly divide larger ones, but it may not
        // necessarily choose the optimal rollup source if this is not the case.
        //
        // e.g. 30m 15m 10m 5m rollups with 15m disabled. We rollup 30m and this would choose 5m, while 10m is optimal.
        Optional<RollupPeriod> sourcePeriod = period.nextSmallest();
        while (sourcePeriod.isPresent() && _maxBackFillByPeriod.getOrDefault(sourcePeriod.get(), 0) == 0) {
            sourcePeriod = sourcePeriod.flatMap(RollupPeriod::nextSmallest);
        }
        return sourcePeriod.map(p -> getDestinationMetricName(metricName, p)).orElse(metricName);
    }

    private String getDestinationMetricName(final String metricName, final RollupPeriod period) {
        return metricName + period.getSuffix();
    }

    private void handleLastDataPointsMessage(final LastDataPointsMessage message) {
        final String sourceMetricName = message.getSourceMetricName();
        final String rollupMetricName = message.getRollupMetricName();
        final RollupPeriod period = message.getPeriod();

        _periodicMetrics.recordCounter("rollup/generator/last_data_point_message/received", 1);
        _periodicMetrics.recordCounter("rollup/generator/last_data_point_message/success", message.isFailure() ? 0 : 1);
        if (message.isFailure()) {
            final Throwable throwable = message.getFailure().orElse(new RuntimeException("Received Failure"));

            LOGGER.warn()
                    .setMessage("Failed to get last data point for metric.")
                    .addData("sourceMetricName", sourceMetricName)
                    .addData("rollupMetricName", rollupMetricName)
                    .setThrowable(throwable)
                    .log();

            getSelf().tell(
                    ThreadLocalBuilder.build(FinishRollupMessage.Builder.class, b -> b
                            .setMetricName(sourceMetricName)
                            .setPeriod(period)
                            .setFailure(throwable)
                    ),
                    ActorRef.noSender());
        } else {
            // Example:
            //
            // Consider a minutely metric that has just hit 00:00 UTC 3 Jan
            //
            //                 22:00         23:00         00:00      startOfLastEligiblePeriod
            //                   |             |             |
            // minutely  x x x x x x x x x x x x x x x x x x x                  N/A
            //   hourly          x             x             |        23:00 2 Jan (1 period  ago)
            //    daily          |             |             |        00:00 1 Jan (2 periods ago)
            //
            // Hourly pulls from the minutely, and sees that the most recently closed period
            // is before the most recent datapoint in minutely, and so we can roll up.
            //
            // Daily pulls from hourly, but the latest hourly datapoint is at 23:00 < 00:00, the end
            // of the most recent period. Therefore we cannot roll-up just yet.
            //
            // Note that this is in a situation where everything behaves as expected. Due to issues with partial success during
            // the save-as operation, a rollup may see an (incorrect) partial result which this code would then interpret as an OK
            // to execute the next larger rollup, thus propagating the error.

            final SortedSet<Instant> startTimes = getRollupTimes(
                    message.getRollupLastDataPointTime(),
                    message.getSourceLastDataPointTime(),
                    period
            );

            if (!startTimes.isEmpty()) {
                try (Metrics metrics = _metricsFactory.create()) {
                    metrics.addAnnotations(_tagger.getTags(message.getSourceMetricName()));

                    final String periodName = period.name().toLowerCase(Locale.getDefault());
                    final Duration backfillAge = Duration.between(startTimes.first(), Instant.now());
                    metrics.setGauge("rollup/generator/backfill_age/" + periodName, backfillAge.toMillis());
                }
            }

            final RollupDefinition.Builder rollupDefBuilder = new RollupDefinition.Builder()
                    .setSourceMetricName(message.getSourceMetricName())
                    .setDestinationMetricName(rollupMetricName)
                    .setPeriod(period)
                    .setAllMetricTags(message.getTags());

            for (final Instant startTime : startTimes) {
                final RollupDefinition defn = rollupDefBuilder.setStartTime(startTime).build();
                _rollupManager.tell(defn, self());
                LOGGER.debug()
                        .setMessage("sent task to _rollupManager")
                        .addData("task", defn)
                        .log();
                _periodicMetrics.recordCounter("rollup/generator/task_sent", 1);
            }

            getSelf().tell(
                    ThreadLocalBuilder.build(FinishRollupMessage.Builder.class, b -> b
                            .setMetricName(message.getSourceMetricName())
                            .setPeriod(message.getPeriod())
                    ),
                    ActorRef.noSender()
            );
        }
    }

    /**
     *
     * @param lastRollupDataPointTime the timestamp of the last rolled-up datapoint , so we know what's already done
     * @param lastSourceDataPointTime the timestamp of the last source-series datapoint, so we know when to roll up until
     * @param period
     * @return
     */
    /* package private */ SortedSet<Instant> getRollupTimes(
            final Optional<Instant> lastRollupDataPointTime,
            final Optional<Instant> lastSourceDataPointTime,
            final RollupPeriod period
    ) {
        final Instant lastRollupDataPoint = lastRollupDataPointTime.orElse(Instant.MIN);
        final Instant startOfLastEligiblePeriod = lastEligiblePeriodStart(
                period,
                lastSourceDataPointTime.orElse(Instant.MIN),
                _clock.instant()
        );
        final Instant rollupPeriodStart = getFirstEligibleBackfillTime(period, lastRollupDataPoint);
        return getRollupableTimes(period, rollupPeriodStart, startOfLastEligiblePeriod);
    }

    private Instant getFirstEligibleBackfillTime(final RollupPeriod period, final Instant lastRollupDataPoint) {
        final int maxBackFillPeriods = _maxBackFillByPeriod.getOrDefault(period, 0);

        final Instant oldestBackfillPoint = period.recentEndTime(_clock.instant())
                .minus(period.periodCountToDuration(maxBackFillPeriods));

        // We either want to start at the oldest backfill point or the start of the period
        // after the last datapoint since it contains data for the period that follows it.
        return lastRollupDataPoint.isBefore(oldestBackfillPoint)
                ? oldestBackfillPoint : period.recentEndTime(lastRollupDataPoint).plus(period.periodCountToDuration(1));
    }

    private SortedSet<Instant> getRollupableTimes(final RollupPeriod period, final Instant startInclusive, final Instant stopInclusive) {
        final SortedSet<Instant> times = new TreeSet<>(); // Docs say "guaranteed log(n) time cost for the basic operations"

        Instant nextTime = startInclusive;

        // We need to rollup every period up to and including the most recent eligible period.
        while (nextTime.isBefore(stopInclusive) || nextTime.equals(stopInclusive)) {
            times.add(nextTime);
            nextTime = nextTime.plus(period.periodCountToDuration(1));
        }
        return times;
    }

    private void handleFinishRollupMessage(final FinishRollupMessage message) {
        _periodicMetrics.recordCounter("rollup/generator/finish_rollup_message/received", 1);
        _periodsInFlight.remove(message.getPeriod());
        if (_periodsInFlight.isEmpty()) {
            getSelf().tell(FETCH_METRIC, ActorRef.noSender());
        }
    }

    private void handleNoMoreMetricsMessage(final NoMoreMetrics message) {
        _periodicMetrics.recordCounter("rollup/generator/metric_names/no_more", 1);
        _periodicMetrics.recordGauge("rollup/generator/metric_names/next_refresh", message.getNextRefreshMillis());
        timers().startSingleTimer("sleepTimer", FETCH_METRIC, _fetchBackoff);
    }

    private LastDataPointsMessage.Builder buildLastDataPointResponse(
            final LastDataPointsMessage.Builder builder,
            final String sourceMetricName,
            final String rollupMetricName,
            final RollupPeriod period,
            final ImmutableMultimap<String, String> tags,
            final MetricsQueryResponse response,
            @Nullable final Throwable failure) {
        builder.setSourceMetricName(sourceMetricName)
                .setRollupMetricName(rollupMetricName)
                .setPeriod(period)
                .setTags(tags);

        if (failure != null) {
            return builder.setFailure(failure);
        }
        final Map<String, Optional<DataPoint>> queryResults =
                response.getQueries()
                        .stream()
                        .flatMap(query -> query.getResults().stream())
                        .collect(ImmutableMap.toImmutableMap(
                                MetricsQueryResponse.QueryResult::getName,
                                // The query limits to 1 data point so it's either the last or empty.
                                queryResult -> queryResult.getValues().stream().findFirst()
                        ));

        // Query results should *only* contain the source and destination metric.
        if (queryResults.size() != 2 || !queryResults.containsKey(sourceMetricName) || !queryResults.containsKey(rollupMetricName)) {
            return builder.setFailure(new UnexpectedQueryResponseException("Unexpected or missing metric names", response));
        }

        // Set source time, if any.
        queryResults.get(sourceMetricName)
                .map(DataPoint::getTime)
                .ifPresent(builder::setSourceLastDataPointTime);

        // Set rollup time, if any.
        queryResults.get(rollupMetricName)
                .map(DataPoint::getTime)
                .ifPresent(builder::setRollupLastDataPointTime);

        return builder;
    }

    private MetricsQuery buildLastDataPointQuery(
            final String sourceMetricName,
            final String rollupMetricName,
            final RollupPeriod period,
            final int backfillPeriods
    ) {
        final Consumer<Metric.Builder> setCommonFields = builder -> builder
                .setAggregators(ImmutableList.of(
                        new Aggregator.Builder()
                                .setName("count")
                                .build())
                )
                .setLimit(1)
                .setOrder(Metric.Order.DESC);

        return new MetricsQuery.Builder()
                        .setStartTime(period.recentEndTime(_clock.instant()).minus(period.periodCountToDuration(backfillPeriods)))
                        .setEndTime(period.recentEndTime(_clock.instant()))
                        .setMetrics(ImmutableList.of(
                             ThreadLocalBuilder.build(Metric.Builder.class, b -> {
                                 setCommonFields.accept(b);
                                 b.setName(sourceMetricName);
                             }),
                             ThreadLocalBuilder.build(Metric.Builder.class, b -> {
                                 setCommonFields.accept(b);
                                 b.setName(rollupMetricName);
                             })
                        )).build();
    }

    /**
     * Find the start-time of the last time interval eligible to be rolled up.
     *
     * @param period the {@link RollupPeriod} to be rolled up
     * @param lastSourceDataPoint the timestamp of the last datapoint of the base time-series to be rolled up
     * @param incompleteAt the time at which we stop being confident that all the base data is present
     *   (probably something close to {@link Instant#now()}
     * @return the start-time of the last time interval that we should roll up
     */
    /* package private */ static Instant lastEligiblePeriodStart(
            final RollupPeriod period,
            final Instant lastSourceDataPoint,
            final Instant incompleteAt
    ) {
        final Instant lastPeriodWithData = period.recentEndTime(lastSourceDataPoint);
        final Instant lastCompletePeriod = period.recentEndTime(incompleteAt).minus(period.periodCountToDuration(1));
        return lastPeriodWithData.isBefore(lastCompletePeriod)
                ? lastPeriodWithData
                : lastCompletePeriod;
    }

    private final ActorRef _metricsDiscovery;
    private final ActorRef _rollupManager;
    private final KairosDbClient _kairosDbClient;
    private final Map<RollupPeriod, Integer> _maxBackFillByPeriod;
    private final FiniteDuration _fetchBackoff;
    private final int _tagLookbackPeriods;
    private final Clock _clock;
    private final PeriodicMetrics _periodicMetrics;
    private final MetricsFactory _metricsFactory;
    private final Tagger _tagger;
    private List<RollupPeriod> _periodsInFlight = Collections.emptyList();

    static final Object FETCH_METRIC = new Object();
    private static final Logger LOGGER = LoggerFactory.getLogger(RollupGenerator.class);
    private static final long KAIROSDB_PERIOD_MILLIS = 1000L * 60 * 60 * 24 * 21;
}
