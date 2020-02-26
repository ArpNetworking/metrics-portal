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
import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.Timer;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import net.sf.oval.constraint.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
                .thenApply(list -> useAvailableRollups(list, metricsQuery, metrics))
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
                .thenApply(list -> new MetricNamesResponse.Builder().setResults(list).build())
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

    private static MetricsQuery useAvailableRollups(
            final List<String> metricNames,
            final MetricsQuery originalQuery,
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
                    } else {
                        metrics.incrementCounter("kairosService/useRollups/bypass", 0);
                        final ImmutableList<String> filteredMetrics = filterMetricNames(metricNames, Optional.of(metricName), Optional.empty(), false);
                        final List<String> rollupMetrics = filteredMetrics
                                .stream()
                                .filter(IS_ROLLUP)
                                .filter(s -> s.length() == metricName.length() + 3)
                                .collect(Collectors.toList());

                        if (rollupMetrics.isEmpty()) {
                            metrics.incrementCounter("kairosService/useRollups/noRollups", 1);
                            // No rollups so execute what we received
                            return metric;
                        } else {
                            // There are rollups, now determine the appropriate one based on the max sampling period in the
                            // aggregators
                            final Optional<SamplingUnit> maxUnit = metric.getAggregators().stream()
                                    .filter(agg -> agg.getAlignSampling().orElse(Boolean.FALSE)) // Filter out non-sampling aligned
                                    .map(Aggregator::getSampling)
                                    .map(sampling -> sampling.map(Sampling::getUnit).orElse(SamplingUnit.MILLISECONDS))
                                    .min(SamplingUnit::compareTo);

                            // No aggregators are sampling aligned so skip as rollups are always aligned
                            if (maxUnit.isPresent()) {

                                final TreeMap<SamplingUnit, String> orderedRollups = new TreeMap<>();
                                rollupMetrics.forEach(name -> {
                                    final Optional<SamplingUnit> rollupUnit =
                                            rollupSuffixToSamplingUnit(name.substring(metricName.length() + 1));
                                    rollupUnit.ifPresent(samplingUnit -> orderedRollups.put(samplingUnit, name));
                                });

                                final Map.Entry<SamplingUnit, String> floorEntry = orderedRollups.floorEntry(maxUnit.get());
                                metrics.incrementCounter("kairosService/useRollups/noMatchingRollup", floorEntry != null ? 1 : 0);
                                final String rollupName = floorEntry != null ? floorEntry.getValue() : metricName;
                                final Metric.Builder metricBuilder = Metric.Builder.<Metric, Metric.Builder>clone(metric)
                                        .setName(rollupName);

                                return metricBuilder.build();
                            } else {
                                metrics.incrementCounter("kairosService/useRollups/notEligible", 1);
                            }

                            return metric;
                        }
                    }
                }).collect(ImmutableList.toImmutableList())));
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

    private static Optional<SamplingUnit> rollupSuffixToSamplingUnit(final String suffix) {
        // Assuming we only rollup to a single sampling unit (e.g. 1 hour or 1 day) and not multiples
        switch (suffix.charAt(suffix.length() - 1)) {
            case 'h':
                return Optional.of(SamplingUnit.HOURS);
            case 'd':
                return Optional.of(SamplingUnit.DAYS);
            case 'w':
                return Optional.of(SamplingUnit.WEEKS);
            case 'm':
                return Optional.of(SamplingUnit.MONTHS);
            case 'y':
                return Optional.of(SamplingUnit.YEARS);
            default:
                return Optional.empty();
        }
    }

    private KairosDbServiceImpl(final Builder builder) {
        this._kairosDbClient = builder._kairosDbClient;
        this._metricsFactory = builder._metricsFactory;
        this._excludedTagNames = builder._excludedTagNames;
    }

    private final KairosDbClient _kairosDbClient;
    private final MetricsFactory _metricsFactory;
    private final ImmutableSet<String> _excludedTagNames;
    private final Cache<String, List<String>> _cache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();
    private final AtomicReference<List<String>> _metricsList = new AtomicReference<>(null);
    private static final String METRICS_KEY = "METRICNAMES";
    private static final String ROLLUP_OVERRIDE = "_!";
    private static final Predicate<String> IS_PT1M = s -> s.startsWith("PT1M/");
    private static final Predicate<String> IS_ROLLUP = s -> s.endsWith("_1h") || s.endsWith("_1d");

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

        @NotNull
        private KairosDbClient _kairosDbClient;
        @NotNull
        private MetricsFactory _metricsFactory;
        @NotNull
        private ImmutableSet<String> _excludedTagNames = ImmutableSet.of();
    }
}
