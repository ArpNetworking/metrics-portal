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

import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.MetricNamesResponse;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.Sets;
import com.typesafe.config.Config;
import org.apache.pekko.actor.AbstractActorWithTimers;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Status;
import org.apache.pekko.pattern.Patterns;
import scala.concurrent.duration.Deadline;
import scala.concurrent.duration.FiniteDuration;

import java.time.Duration;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jakarta.inject.Inject;

/**
 * Actor for discovering the list of metrics available to be rolled up on a periodic basis.
 *
 * This actor maintains an internal set of metric names and acts as a source for downstream
 * actors that perform the actual rollups.  This is intended to be used as a singleton in the
 * cluster.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public final class MetricsDiscovery extends AbstractActorWithTimers {

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals(
                        FETCH_MSG,
                        work -> {
                            getTimers().startSingleTimer(REFRESH_TIMER, FETCH_MSG, _fetchInterval);
                            _refreshDeadline = _fetchInterval.fromNow();
                            fetchMetricsForRollup();
                        })
                .matchEquals(
                        RECORD_METRICS_MSG,
                        work -> _periodicMetrics.recordGauge("rollup/discovery/queue_size", _metricsSet.size())
                )
                .match(MetricNamesResponse.class, this::updateMetricsSet)
                .match(
                        Status.Failure.class,
                        failure -> LOGGER.warn("Failed to get metrics from Kairos", failure.cause()))
                .match(
                        MetricFetch.class,
                        work -> {
                            final Optional<String> metricName = getNextMetric();
                            if (metricName.isPresent()) {
                                getSender().tell(metricName.get(), getSelf());
                            } else {
                                getSender().tell(new NoMoreMetrics(_refreshDeadline), getSelf());
                            }
                        })
                .build();
    }

    /**
     * Metrics discovery constructor.
     *
     * @param configuration play configuration object
     * @param kairosDbClient client to use for fetching metric names
     * @param periodicMetrics periodic metrics client
     */
    @Inject
    public MetricsDiscovery(
            final Config configuration,
            final KairosDbClient kairosDbClient,
            final PeriodicMetrics periodicMetrics) {
        _fetchInterval = ConfigurationHelper.getFiniteDuration(configuration, "rollup.fetch.interval");
        _whiteList = toPredicate(configuration.getStringList("rollup.metric.whitelist"), true);
        _blackList = toPredicate(configuration.getStringList("rollup.metric.blacklist"), false);
        _kairosDbClient = kairosDbClient;
        _periodicMetrics = periodicMetrics;
        _metricsSet = new LinkedHashSet<>();
        _setIterator = _metricsSet.iterator();
        _refreshDeadline = Deadline.now();
        getSelf().tell(FETCH_MSG, ActorRef.noSender());
        getTimers().startTimerAtFixedRate(METRICS_TIMER, RECORD_METRICS_MSG, METRICS_INTERVAL);
    }

    private void fetchMetricsForRollup() {
        final long startTime = System.nanoTime();
        Patterns.pipe(
                _kairosDbClient.queryMetricNames()
                .whenComplete((response, failure) -> {
                    // Record metrics
                    _periodicMetrics.recordCounter("rollup/discovery/metric_names/success", failure == null ? 1 : 0);
                    _periodicMetrics.recordTimer(
                            "rollup/discovery/metric_names/latency",
                            System.nanoTime() - startTime,
                            Optional.of(TimeUnit.NANOSECONDS));
                }),
                getContext().dispatcher())
                .to(getSelf());
    }

    private void updateMetricsSet(final MetricNamesResponse response) {
        final Set<String> newMetricsSet = Sets.newLinkedHashSet();
        filterMetricNames(response.getResults(), _whiteList, _blackList).forEach(newMetricsSet::add);
        _periodicMetrics.recordCounter("rollup/discovery/discovered", newMetricsSet.size());
        _metricsSet.addAll(newMetricsSet);
        _setIterator = _metricsSet.iterator();
    }

    private Optional<String> getNextMetric() {
        final String next;
        if (_setIterator.hasNext()) {
            next = _setIterator.next();
            _setIterator.remove();
        } else {
            next = null;
        }

        return Optional.ofNullable(next);
    }

    static Stream<String> filterMetricNames(
            final Collection<String> metricNames,
            final Predicate<String> whiteList,
            final Predicate<String> blackList) {
        return metricNames.stream()
                .filter(ROLLUP_METRIC_PREDICATE.negate())
                .filter(whiteList)
                .filter(blackList.negate());
    }

    static Predicate<String> toPredicate(final List<String> regexList, final boolean defaultResult) {
        return regexList
                .stream()
                .map(Pattern::compile)
                .map(Pattern::asPredicate)
                .reduce(Predicate::or).orElse(t -> defaultResult);
    }

    private final FiniteDuration _fetchInterval;
    private final KairosDbClient _kairosDbClient;
    private final PeriodicMetrics _periodicMetrics;
    private final Set<String> _metricsSet;
    private Iterator<String> _setIterator;
    private Deadline _refreshDeadline;
    private final Predicate<String> _whiteList;
    private final Predicate<String> _blackList;

    private static final String RECORD_METRICS_MSG = "record_metrics";
    private static final String METRICS_TIMER = "metrics_timer";
    private static final Duration METRICS_INTERVAL = Duration.ofSeconds(1);
    private static final String REFRESH_TIMER = "refresh_timer";
    private static final Object FETCH_MSG = new Object();
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsDiscovery.class);
    private static final Predicate<String> ROLLUP_METRIC_PREDICATE = Pattern.compile("^.*_1[hd]$").asPredicate();
}
