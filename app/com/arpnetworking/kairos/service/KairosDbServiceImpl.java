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
package com.arpnetworking.kairos.service;

import akka.actor.ActorRef;
import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.Aggregator;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricNamesResponse;
import com.arpnetworking.kairos.client.models.MetricTags;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.Sampling;
import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.arpnetworking.kairos.client.models.TagNamesResponse;
import com.arpnetworking.kairos.client.models.TagsQuery;
import com.arpnetworking.kairos.config.MetricsQueryConfig;
import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.Timer;
import com.arpnetworking.rollups.ConsistencyChecker;
import com.arpnetworking.rollups.RollupMetric;
import com.arpnetworking.rollups.RollupPeriod;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import net.sf.oval.constraint.NotNull;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Defines a service provider that augments calls to a KairosDB backend server.
 * <p>
 * This class applies logic to KairosDB requests to modify the behavior of KairosDB
 * in a way that provides specific behaviors that wouldn't be acceptable for a more
 * generic service like KairosDB.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public final class KairosDbServiceImpl implements KairosDbService {

    @Override
    public CompletionStage<MetricsQueryResponse> queryMetricTags(final TagsQuery tagsQuery) {
        final Metrics metrics = _metricsFactory.create();
        final Timer timer = metrics.createTimer("kairosService/queryMetricTags/request");
        final ImmutableSet<String> requestedTags = tagsQuery.getMetrics()
                .stream()
                .flatMap(m -> m.getTags().keySet().stream())
                .collect(ImmutableSet.toImmutableSet());
        // Filter out rollup metric overrides and forward the query
        return filterRollupOverrides(tagsQuery)
                .thenCompose(_kairosDbClient::queryMetricTags)
                .thenApply(response -> filterExcludedTags(response, requestedTags))
                .whenComplete((result, error) -> {
                    timer.stop();
                    metrics.incrementCounter("kairosService/queryMetricTags/success", error == null ? 1 : 0);
                    metrics.close();
                });
    }

    @Override
    public CompletionStage<MetricsQueryResponse> queryMetrics(final MetricsQuery metricsQuery) {
        final Metrics metrics = _metricsFactory.create();
        final Timer timer = metrics.createTimer("kairosService/queryMetrics/request");
        final ImmutableSet<String> requestedTags = metricsQuery.getMetrics()
                .stream()
                .flatMap(m -> m.getTags().keySet().stream())
                .collect(ImmutableSet.toImmutableSet());
        return getMetricNames(metrics)
                .thenApply(names -> useAvailableRollups(names, metricsQuery, _metricsQueryConfig, metrics))
                .whenComplete((query, throwable) -> {
                    if (throwable != null) {
                        // Something downstream in the pipeline should get the error.
                        return;
                    }

                    _rewrittenQueryConsumer.accept(query);
                })
                .thenCompose(_kairosDbClient::queryMetrics)
                .thenApply(response -> filterExcludedTags(response, requestedTags))
                .whenComplete((result, error) -> {
                    timer.stop();
                    metrics.incrementCounter("kairosService/queryMetrics/success", error == null ? 1 : 0);
                    metrics.close();
                });
    }

    @Override
    public CompletionStage<MetricNamesResponse> queryMetricNames(
            final Optional<String> containing,
            final Optional<String> prefix,
            final boolean filterRollups) {
        final Metrics metrics = _metricsFactory.create();
        final Timer timer = metrics.createTimer("kairosService/queryMetricNames/request");

        return getMetricNames(metrics)
                .thenApply(list -> filterMetricNames(list, containing, prefix, filterRollups))
                .thenApply(list -> ThreadLocalBuilder.build(MetricNamesResponse.Builder.class, b -> b.setResults(list)))
                .whenComplete((result, error) -> {
                    timer.stop();
                    metrics.incrementCounter("kairosService/queryMetricNames/success", error == null ? 1 : 0);
                    metrics.addAnnotation("containing", containing.isPresent() ? "true" : "false");
                    if (result != null) {
                        metrics.incrementCounter("kairosService/queryMetricNames/count", result.getResults().size());
                    }
                    metrics.close();
                });
    }

    @Override
    public CompletionStage<TagNamesResponse> listTagNames() {
        final Metrics metrics = _metricsFactory.create();
        final Timer timer = metrics.createTimer("kairosService/listTagNames/request");

        return _kairosDbClient.listTagNames()
                .thenApply(response -> ThreadLocalBuilder.<TagNamesResponse, TagNamesResponse.Builder>clone(response)
                        .setResults(
                                response.getResults()
                                        .stream()
                                        .filter(e -> !_excludedTagNames.contains(e))
                                        .collect(ImmutableSet.toImmutableSet()))
                        .build())
                .whenComplete((result, error) -> {
                    timer.stop();
                    metrics.incrementCounter("kairosService/listTagNames/success", error == null ? 1 : 0);
                    if (result != null) {
                        metrics.incrementCounter("kairosService/listTagNames/count", result.getResults().size());
                    }
                    metrics.close();
                });
    }

    private static ImmutableList<String> filterMetricNames(
            final List<String> metricNames,
            final Optional<String> containing,
            final Optional<String> prefix,
            final boolean filterRollups) {

        final Predicate<String> baseFilter;
        if (filterRollups) {
            baseFilter = IS_ROLLUP.negate();
        } else {
            baseFilter = s -> true;
        }

        final Predicate<String> containsFilter;
        if (containing.isPresent() && !containing.get().isEmpty()) {
            final String lowerContaining = containing.get().toLowerCase(Locale.getDefault());
            containsFilter = s -> s.toLowerCase(Locale.getDefault()).contains(lowerContaining);
        } else {
            containsFilter = s -> true;
        }

        final Predicate<String> prefixFilter;
        if (prefix.isPresent() && !prefix.get().isEmpty()) {
            final String lowerPrefix = prefix.get().toLowerCase(Locale.getDefault());
            prefixFilter = s -> s.toLowerCase(Locale.getDefault()).startsWith(lowerPrefix);
        } else {
            prefixFilter = s -> true;
        }

        return metricNames.stream()
                .filter(baseFilter)
                .filter(IS_PT1M.negate())
                .filter(containsFilter)
                .filter(prefixFilter)
                .collect(ImmutableList.toImmutableList());
    }


    private CompletionStage<List<String>> getMetricNames(final Metrics metrics) {
        final List<String> metricsNames = _cache.getIfPresent(METRICS_KEY);

        final CompletionStage<List<String>> response;
        if (metricsNames != null) {
            metrics.incrementCounter("kairosService/metricNames/cache", 1);
            response = CompletableFuture.completedFuture(metricsNames);
        } else {
            metrics.incrementCounter("kairosService/metricNames/cache", 0);
            // TODO(brandon): Investigate refreshing eagerly or in the background
            // Refresh the cache
            final Timer timer = metrics.createTimer("kairosService/metricNames/request");
            final CompletionStage<List<String>> queryResponse = _kairosDbClient.queryMetricNames()
                    .whenComplete((result, error) -> {
                        timer.stop();
                        metrics.incrementCounter("kairosService/metricNames/success", error == null ? 1 : 0);
                    })
                    .thenApply(MetricNamesResponse::getResults)
                    .thenApply(list -> {
                        _cache.put(METRICS_KEY, list);
                        _metricsList.set(list);
                        return list;
                    });

            final List<String> metricsList = _metricsList.get();
            if (metricsList != null) {
                response = CompletableFuture.completedFuture(metricsList);
            } else {
                response = queryResponse;
            }
        }

        return response;
    }

    private static void maybeQueueConsistencyChecks(final MetricsQuery query, final Throwable throwable) {
        if (throwable != null) {
            // Something downstream in the calling pipeline should get the error.
            return;
        }

        if (!query.getStartTime().isPresent()) {
            // TODO: log
            return;
        }

        if (!query.getEndTime().isPresent()) {
            // TODO: log
            return;
        }

        final Instant startTime = query.getStartTime().get();
        final Instant endTime = query.getEndTime().get();

        query.getMetrics().stream()
                .map(Metric::getName)
                .map(RollupMetric::fromRollupMetricName)
                .forEach(rollupMetricMaybe ->
                    rollupMetricMaybe.ifPresent(rollupMetric -> {
                        // TODO: maybe a for loop would be better
                        periodStreamForInterval(startTime, endTime, rollupMetric)
                            .map(periodStartTime -> new ConsistencyChecker.Task.Builder()
                                    .setSourceMetricName(rollupMetric.getBaseMetricName())
                                    .setRollupMetricName(rollupMetric.getRollupMetricName())
                                    .setStartTime(periodStartTime)
                                    .setTrigger(ConsistencyChecker.Task.Trigger.QUERIED)
                                    .setPeriod(rollupMetric.getPeriod())
                                    .build())
                            // TODO: move everything below this line to another func
                            .randomFilter()
                            // TODO: wait for consistency checker to process the message before sending more?
                            .forEach(task -> consistencyChecker.tell(task, ActorRef.noSender()));
                })
            );
    }

    private static Stream<Instant> periodStreamForInterval(Instant startTime, Instant endTime, RollupMetric rollupMetric) {
        final PeriodIterator periods = new PeriodIterator(startTime, endTime, rollupMetric.getPeriod());
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        periods,
                        Spliterator.ORDERED)
                , false);
    }


    static class PeriodIterator implements Iterator<Instant> {
        Instant _periodStart;
        final Instant _end;
        final RollupPeriod _rollupPeriod;

        public PeriodIterator(final Instant _start, final Instant _end, RollupPeriod _rollupPeriod) {
            this._periodStart = _start;
            this._end = _end;
            this._rollupPeriod = _rollupPeriod;
        }

        @Override
        public boolean hasNext() {
            return _periodStart.isBefore(_end);
        }

        @Override
        public Instant next() {
            final Instant ret = _periodStart;
            _periodStart = _rollupPeriod.nextPeriodStart(_periodStart);
            return ret;
        }
    }

    /* package private */ static MetricsQuery useAvailableRollups(
            final List<String> metricNames,
            final MetricsQuery originalQuery,
            final MetricsQueryConfig queryConfig,
            final Metrics metrics) {
        return ThreadLocalBuilder.clone(
                originalQuery,
                MetricsQuery.Builder.class,
                newQueryBuilder -> newQueryBuilder.setMetrics(originalQuery.getMetrics().stream().map(metric -> {
                    // Check to see if there are any rollups for this metrics
                    final String metricName = metric.getName();
                    if (metricName.endsWith(ROLLUP_OVERRIDE)) {
                        metrics.incrementCounter("kairosService/useRollups/bypass", 1);
                        // Special case a _! suffix to not apply rollup selection
                        // Drop the suffix and forward the request
                        return Metric.Builder.<Metric, Metric.Builder>clone(metric)
                                .setName(metricName.substring(0, metricName.length() - 2))
                                .build();
                    }
                    metrics.incrementCounter("kairosService/useRollups/bypass", 0);
                    final ImmutableList<String> filteredMetrics = filterMetricNames(
                            metricNames,
                            Optional.of(metricName),
                            Optional.empty(),
                            false
                    );
                    final List<RollupMetric> rollupMetrics = filteredMetrics
                            .stream()
                            .map(RollupMetric::fromRollupMetricName)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .collect(Collectors.toList());

                    if (rollupMetrics.isEmpty()) {
                        metrics.incrementCounter("kairosService/useRollups/noRollups", 1);
                        // No rollups so execute what we received
                        return metric;
                    }

                    // There are rollups and queries are enabled, now determine the coarsest rollup-period
                    //   that we might be able to use without changing the query's meaning.
                    final Optional<SamplingUnit> maxUsableRollupUnit = getMaxUsableRollupUnit(metric);


                    if (maxUsableRollupUnit.isPresent()) {
                        final Optional<RollupMetric> rollupMetric = getCoarsestUsableRollupMetric(
                                metricName,
                                rollupMetrics,
                                queryConfig,
                                maxUsableRollupUnit.get()
                        );
                        metrics.incrementCounter("kairosService/useRollups/noMatchingRollup", rollupMetric.isPresent() ? 0 : 1);
                        final String rewrittenMetricName = rollupMetric.map(RollupMetric::getRollupMetricName).orElse(metricName);
                        return Metric.Builder.<Metric, Metric.Builder>clone(metric)
                                .setName(rewrittenMetricName)
                                .build();
                    } else {
                        // No aggregators are sampling aligned so skip as rollups are always aligned
                        metrics.incrementCounter("kairosService/useRollups/notEligible", 1);
                        return metric;
                    }
                }).collect(ImmutableList.toImmutableList())));
    }

    /**
     * For a metric, find the corresponding rollup metric with the longest period not exceeding some threshold.
     *
     * @param metricName The metric we want to find a rollup for.
     * @param rollupMetrics A list of all rollup metrics corresponding to the given metric.
     * @param queryConfig Used to tell which rollup metrics are enabled when querying this metric.
     * @param maxUsableRollupUnit The longest rollup-period we're willing to accept (for fear of changing the query's results).
     * @return The given enabled {@code rollupMetric} with the greatest period not exceeding the threshold (if any).
     */
    /* package private */ static Optional<RollupMetric> getCoarsestUsableRollupMetric(
            final String metricName,
            final List<RollupMetric> rollupMetrics,
            final MetricsQueryConfig queryConfig,
            final SamplingUnit maxUsableRollupUnit
        ) {
        final Set<SamplingUnit> enabledRollups = queryConfig.getQueryEnabledRollups(metricName);

        final TreeMap<SamplingUnit, RollupMetric> orderedRollups = new TreeMap<>();
        rollupMetrics.forEach(rollupMetric -> {
            final SamplingUnit rollupUnit = rollupMetric.getPeriod().getSamplingUnit();
            if (enabledRollups.contains(rollupUnit)) {
                orderedRollups.put(rollupUnit, rollupMetric);
            }
        });

        return Optional.ofNullable(orderedRollups.floorEntry(maxUsableRollupUnit)).map(Map.Entry::getValue);
    }

    /**
     * Get the coarsest rollup time period that could be used to rewrite a query without affecting its results.
     *
     * @param metric The metric-query that we might want to rewrite to make use of rollups.
     * @return The largest {@link SamplingUnit} that the query is insensitive to aggregation over
     *     (or {@code empty}, if the query is sensitive to all pre-aggregation).
     */
    /* package private */ static Optional<SamplingUnit> getMaxUsableRollupUnit(final Metric metric) {
        /* By querying a rollup (say, the hourly rollup), we're effectively putting a sampling-aligned `merge(1h)`
         *   in front of the metric's aggregators.
         * For this to not affect the results, the first aggregator must be sampling-aligned, and must aggregate over
         *   some integer number of hours.
         * (Empirically, non-range aggregators (e.g. `div`, `filter`) commute with `merge`, so we can ignore them.)
         * If the query has no range aggregators, adding a `merge` aggregator clearly changes the results.
         */
        for (final Aggregator aggregator : metric.getAggregators()) {
            final Optional<Sampling> sampling = aggregator.getSampling();
            if (!sampling.isPresent()) {
                // Empirically, non-range aggregators commute with `merge`, so we can ignore them
                continue;
            }
            if (!aggregator.getAlignSampling().orElse(false)) {
                // The first range aggregator is not sampling-aligned; inserting any sampling-aligned range aggregator before it
                // will change its semantics
                return Optional.empty();
            }
            // The first range aggregator is sampling-aligned, so inserting a sampling-aligned merge aggregator with the same period
            //   should be a behavioral no-op
            return Optional.of(sampling.get().getUnit());
        }
        if (metric.getAggregators().stream().anyMatch(agg -> agg.getSampling().isPresent())) {
            LOGGER.error()
                .setMessage("assertion failed: metric has range aggregators when we thought we'd ruled that out")
                .addData("metric", metric)
                .log();
        }
        // There are no range aggregators -- inserting one will change semantics
        return Optional.empty();
    }

    private CompletionStage<TagsQuery> filterRollupOverrides(final TagsQuery originalQuery) {
        return CompletableFuture.completedFuture(
                ThreadLocalBuilder.clone(
                        originalQuery,
                        TagsQuery.Builder.class,
                        newQueryBuilder -> newQueryBuilder.setMetrics(originalQuery.getMetrics().stream().map(metric -> {
                                // Check to see if there are any rollups for this metrics
                                final String metricName = metric.getName();
                                if (metricName.endsWith(ROLLUP_OVERRIDE)) {
                                    // Special case a _! suffix to not apply rollup selection
                                    // Drop the suffix and forward the request
                                    return ThreadLocalBuilder.clone(
                                    metric,
                                    MetricTags.Builder.class, b -> b.setName(metricName.substring(0, metricName.length() - 2)));
                                } else {
                                    return metric;
                                }
                            }).collect(ImmutableList.toImmutableList()))));
    }

    private MetricsQueryResponse filterExcludedTags(
            final MetricsQueryResponse originalResponse,
            final ImmutableSet<String> retainedTags) {
        return ThreadLocalBuilder.clone(
                originalResponse,
                MetricsQueryResponse.Builder.class,
                responseBuilder -> responseBuilder.setQueries(
                        originalResponse.getQueries()
                                .stream()
                                .map(originalQuery -> ThreadLocalBuilder.clone(
                                        originalQuery,
                                        MetricsQueryResponse.Query.Builder.class,
                                        queryBuilder -> queryBuilder.setResults(
                                                originalQuery.getResults()
                                                        .stream()
                                                        .map(result -> filterQueryResultTags(result, retainedTags))
                                                        .collect(ImmutableList.toImmutableList()))))
                                .collect(ImmutableList.toImmutableList())));
    }

    private MetricsQueryResponse.QueryResult filterQueryResultTags(
            final MetricsQueryResponse.QueryResult originalResult,
            final ImmutableSet<String> retainedTags) {
        return ThreadLocalBuilder.clone(
                originalResult,
                MetricsQueryResponse.QueryResult.Builder.class,
                resultBuilder -> resultBuilder.setTags(
                        originalResult.getTags()
                                .entries()
                                .stream()
                                .filter(e -> !_excludedTagNames.contains(e.getKey()) || retainedTags.contains(e.getKey()))
                                .collect(ImmutableListMultimap.toImmutableListMultimap(
                                        e -> e.getKey(),
                                        e -> e.getValue()))));
    }

    private KairosDbServiceImpl(final Builder builder) {
        this._kairosDbClient = builder._kairosDbClient;
        this._metricsFactory = builder._metricsFactory;
        this._excludedTagNames = builder._excludedTagNames;
        this._metricsQueryConfig = builder._metricsQueryConfig;
        this._rewrittenQueryConsumer = builder._rewrittenQueryConsumer;
    }

    private final KairosDbClient _kairosDbClient;
    private final MetricsFactory _metricsFactory;
    private final ImmutableSet<String> _excludedTagNames;
    private final MetricsQueryConfig _metricsQueryConfig;
    private final Consumer<MetricsQuery> _rewrittenQueryConsumer;
    private final Cache<String, List<String>> _cache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();
    private final AtomicReference<List<String>> _metricsList = new AtomicReference<>(null);
    private static final String METRICS_KEY = "METRICNAMES";
    private static final String ROLLUP_OVERRIDE = "_!";
    private static final Predicate<String> IS_PT1M = s -> s.startsWith("PT1M/");
    private static final Predicate<String> IS_ROLLUP = s -> RollupMetric.fromRollupMetricName(s).isPresent();
    private static final Logger LOGGER = LoggerFactory.getLogger(KairosDbServiceImpl.class);

    /**
     * Implementation of the builder pattern for {@link KairosDbServiceImpl}.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
     */
    public static final class Builder extends OvalBuilder<KairosDbServiceImpl> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(KairosDbServiceImpl::new);
        }

        /**
         * Sets the {@link KairosDbClient} to use. Cannot be null.
         *
         * @param value the {@link KairosDbClient} to use
         * @return this {@link Builder}
         */
        public Builder setKairosDbClient(final KairosDbClient value) {
            _kairosDbClient = value;
            return this;
        }

        /**
         * Sets the {@link MetricsFactory} to use. Cannot be null.
         *
         * @param value the {@link MetricsFactory} to use
         * @return this {@link Builder}
         */
        public Builder setMetricsFactory(final MetricsFactory value) {
            _metricsFactory = value;
            return this;
        }

        /**
         * Sets the tag names to exclude. Cannot be null. Optional. Default is
         * an empty set (no tag names are excluded).
         *
         * @param value the tag names to exclude
         * @return this {@link Builder}
         */
        public Builder setExcludedTagNames(final ImmutableSet<String> value) {
            _excludedTagNames = value;
            return this;
        }

        /**
         * Sets the MetricsQueryConfig. Cannot be null.
         *
         * @param value the query config
         * @return this {@link Builder}
         */
        public Builder setMetricsQueryConfig(final MetricsQueryConfig value) {
            _metricsQueryConfig = value;
            return this;
        }

        public Builder setRewrittenQueryConsumer(final Consumer<MetricsQuery> consumer) {
            _rewrittenQueryConsumer = consumer;
            return this;
        }

        @NotNull
        private KairosDbClient _kairosDbClient;
        @NotNull
        private MetricsFactory _metricsFactory;
        @NotNull
        private ImmutableSet<String> _excludedTagNames = ImmutableSet.of();
        @NotNull
        private MetricsQueryConfig _metricsQueryConfig;
        @NotNull
        private Consumer<MetricsQuery> _rewrittenQueryConsumer = (query -> {});
    }
}
