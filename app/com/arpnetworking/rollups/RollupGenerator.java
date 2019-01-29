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
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.Sampling;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.typesafe.config.Config;
import scala.concurrent.duration.FiniteDuration;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
                .matchEquals(FETCH_METRIC, work -> _metricsDiscovery.tell(MetricFetch.getInstance(), getSelf()))
                .match(String.class, this::fetchMetricTags)
                .match(TagNamesMessage.class, this::handleTagNamesMessage)
                .match(LastDataPointMessage.class, this::handleLastDataPointMessage)
                .match(FinishRollupMessage.class, this::handleFinishRollupMessage)
                .match(NoMoreMetrics.class, noMore -> timers().startSingleTimer("sleepTimer", FETCH_METRIC, _fetchBackoff))
                .build();
    }


    /**
     * RollupGenerator actor constructor.
     *
     * @param configuration play configuration
     * @param metricsDiscovery actor ref to metrics discovery actor
     * @param kairosDbClient kairosdb client
     * @param clock clock to use for time calculations
     */
    @Inject
    public RollupGenerator(
            final Config configuration,
            @Named("RollupsMetricsDiscovery") final ActorRef metricsDiscovery,
            final KairosDbClient kairosDbClient,
            final Clock clock) {
        _metricsDiscovery = metricsDiscovery;
        _kairosDbClient = kairosDbClient;
        _clock = clock;
        _maxBackFillPeriods = configuration.getInt("rollup.maxBackFill.periods");
        _fetchBackoff = ConfigurationHelper.getFiniteDuration(configuration, "rollup.fetch.backoff");
    }

    @Override
    public void preStart() {
        getSelf().tell(FETCH_METRIC, ActorRef.noSender());
    }

    private void fetchMetricTags(final String metricName) {
        PatternsCS.pipe(_kairosDbClient.queryMetricTags(
                new MetricsQuery.Builder()
                        .setStartTime(Instant.ofEpochMilli(0))
                        .setMetrics(ImmutableList.of(
                                new Metric.Builder()
                                        .setName(metricName)
                                        .build()
                        ))
                        .build()).handle((response, failure) -> {
                    if (failure != null) {
                        return new TagNamesMessage.Builder()
                                .setFailure(true)
                                .setMetricName(metricName)
                                .setThrowable(failure)
                                .build();
                    } else {
                        return new TagNamesMessage.Builder()
                                .setFailure(false)
                                .setMetricName(metricName)
                                .setTagNames(response.getQueries().get(0).getResults().get(0).getTags().keySet())
                                .build();
                    }
                }),
                getContext().dispatcher())
                .to(getSelf());

    }

    private void handleTagNamesMessage(final TagNamesMessage message) {
        if (message.isFailure()) {
            // TODO(gmarkham): Metrics
            LOGGER.warn(
                    "Failed to get tag names for metric " + message.getMetricName(),
                    message.getThrowable());

            // Get the next metric
            getSelf().tell(FETCH_METRIC, ActorRef.noSender());
        } else {
            // TODO(gmarkham): Metrics
            _periodsInFlight = Lists.newArrayList(RollupPeriod.values());
            final String metricName = message.getMetricName();
            for (final RollupPeriod period : RollupPeriod.values()) {
                PatternsCS.pipe(
                        fetchLastDataPoint(metricName + period.getSuffix(), period)
                                .handle((response, failure) ->
                                        buildLastDataPointResponse(metricName, period, message.getTagNames(), response, failure)
                                ), getContext().dispatcher())
                        .to(getSelf());
            }
        }
    }

    private void handleLastDataPointMessage(final LastDataPointMessage message) {
        if (message.isFailure()) {
            // TODO(gmarkham): Metrics
            LOGGER.warn(
                    "Failed to get last data point for metric " + message.getMetricName(),
                    message.getThrowable());
            getSelf().tell(
                    new FinishRollupMessage.Builder()
                            .setMetricName(message.getMetricName())
                            .setPeriod(message.getPeriod())
                            .build(),
                    ActorRef.noSender());
        } else {
            // TODO(gmarkham): Metrics
            final Instant recentPeriodEndTime = message.getPeriod()
                    .recentEndTime(_clock.instant());

            // If the most recent period aligned end time is after the most recent datapoint then
            // we need to run the rollup, otherwise we can skip this and just send a finish message.
            if (recentPeriodEndTime.isAfter(message.getLastDataPointTime().orElse(Instant.EPOCH))) {
                PatternsCS.pipe(
                        runRollupQuery(message)
                                .handle((response, failure) -> new FinishRollupMessage.Builder()
                                        .setMetricName(message.getMetricName())
                                        .setPeriod(message.getPeriod())
                                        .setFailure(failure != null)
                                        .setThrowable(failure)
                                        .build()), getContext().dispatcher())
                        .to(getSelf());
            } else {
                getSelf().tell(
                        new FinishRollupMessage.Builder()
                                .setMetricName(message.getMetricName())
                                .setPeriod(message.getPeriod())
                                .setFailure(false)
                                .build(),
                        ActorRef.noSender()
                );
            }
        }
    }

    private void handleFinishRollupMessage(final FinishRollupMessage message) {
        _periodsInFlight.remove(message.getPeriod());
        if (_periodsInFlight.isEmpty()) {
            getSelf().tell(FETCH_METRIC, ActorRef.noSender());
        }
    }

    private LastDataPointMessage buildLastDataPointResponse(
            final String metricName, final RollupPeriod period, final ImmutableSet<String> tags,
            final MetricsQueryResponse response, @Nullable final Throwable failure) {
        final LastDataPointMessage.Builder builder = new LastDataPointMessage.Builder()
                .setMetricName(metricName)
                .setPeriod(period);
        if (failure != null) {
            builder.setFailure(true);
            builder.setThrowable(failure);
        } else {
            builder.setFailure(false);
            builder.setTags(tags);
            builder.setLastDataPointTime(response.getQueries().get(0).getResults().get(0).getValues().get(0).getTime());
        }
        return builder.build();
    }

    private CompletionStage<MetricsQueryResponse> fetchLastDataPoint(final String metricName, final RollupPeriod period) {
        return _kairosDbClient.queryMetrics(
                new MetricsQuery.Builder()
                        .setStartTime(period.recentEndTime(_clock.instant()).minus(period.periodCountToDuration(_maxBackFillPeriods)))
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

    private CompletionStage<MetricsQueryResponse> runRollupQuery(final LastDataPointMessage message) {
        final MetricsQuery.Builder queryBuilder = new MetricsQuery.Builder();
        final Metric.Builder metricBuilder = new Metric.Builder();
        final String rollupMetricName = message.getMetricName() + message.getPeriod().getSuffix();
        final RollupPeriod period = message.getPeriod();

        final Instant lastDataPoint = message.getLastDataPointTime().orElse(Instant.EPOCH);
        final Instant oldestBackfillPoint = period.recentEndTime(_clock.instant()).minus(period.periodCountToDuration(_maxBackFillPeriods));
        if (lastDataPoint.isBefore(oldestBackfillPoint)) {
            queryBuilder.setStartTime(oldestBackfillPoint);
        } else {
            queryBuilder.setStartTime(lastDataPoint);
        }

        queryBuilder.setEndTime(period.recentEndTime(_clock.instant()));

        metricBuilder.setName(message.getMetricName());
        if (!message.getTags().isEmpty()) {
            metricBuilder.setGroupBy(ImmutableList.of(
                    new MetricsQuery.GroupBy.Builder()
                    .setName("tag").addOtherArg("tags", message.getTags())
                    .build()
            ));
        }
        metricBuilder.setAggregators(ImmutableList.of(
                new Aggregator.Builder()
                        .setName("merge")
                        .setSampling(Optional.of(
                                new Sampling.Builder()
                                        .setValue(1)
                                        .setUnit(message.getPeriod().getSamplingUnit())
                                        .build()))
                        .setAlignSampling(Optional.of(true))
                        .setAlignEndTime(Optional.of(true))
                        .build(),
                new Aggregator.Builder()
                        .setName("saveAs")
                        .setOtherArgs(ImmutableMap.of("save_as", rollupMetricName))
                        .build()
        ));

        return _kairosDbClient.queryMetrics(
                queryBuilder.setMetrics(ImmutableList.of(metricBuilder.build()))
                .build());
    }

    private final ActorRef _metricsDiscovery;
    private final KairosDbClient _kairosDbClient;
    private final int _maxBackFillPeriods;
    private final FiniteDuration _fetchBackoff;
    private final Clock _clock;
    private List<RollupPeriod> _periodsInFlight = Collections.emptyList();
    static final Object FETCH_METRIC = new Object();
    private static final Logger LOGGER = LoggerFactory.getLogger(RollupGenerator.class);
}
