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

import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.Aggregator;
import com.arpnetworking.kairos.client.models.KairosMetricNamesQueryResponse;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.RollupResponse;
import com.arpnetworking.kairos.client.models.RollupTask;
import com.arpnetworking.kairos.client.models.Sampling;
import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;

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
public class KairosDbServiceImpl implements KairosDbService {


    /**
     * Public constructor.
     *
     * @param kairosDbClient - Client to use to make requests to backend kairosdb
     */
    public KairosDbServiceImpl(final KairosDbClient kairosDbClient) {
        this._kairosDbClient = kairosDbClient;
    }

    @Override
    public CompletionStage<MetricsQueryResponse> queryMetricTags(final MetricsQuery query) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<List<RollupTask>> queryRollups() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<RollupResponse> createRollup(final RollupTask rollupTask) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<RollupResponse> updateRollup(final String id, final RollupTask rollupTask) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<Void> deleteRollup(final String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<MetricsQueryResponse> queryMetrics(final MetricsQuery metricsQuery) {
        return getMetricNames()
                .thenApply(list -> useAvailableRollups(list, metricsQuery))
                .thenCompose(_kairosDbClient::queryMetrics);
    }

    /**
     * Caching metricNames call.
     *
     * @param containing simple string match filter for metric names
     * @return Cached metric names, filtered by the query string.
     */
    @Override
    public CompletionStage<KairosMetricNamesQueryResponse> queryMetricNames(
            final Optional<String> containing,
            final boolean filterRollups) {

        return getMetricNames()
                .thenApply(list -> filterMetricNames(list, containing, filterRollups))
                .thenApply(list -> new KairosMetricNamesQueryResponse.Builder().setResults(list).build());
    }

    private static ImmutableList<String> filterMetricNames(
            final List<String> metricNames,
            final Optional<String> containing,
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

        return metricNames.stream()
                .filter(baseFilter)
                .filter(IS_PT1M.negate())
                .filter(containsFilter)
                .collect(Collectors.collectingAndThen(Collectors.toList(), ImmutableList::copyOf));
    }


    private CompletionStage<List<String>> getMetricNames() {
        final List<String> metrics = _cache.getIfPresent(METRICS_KEY);

        final CompletionStage<List<String>> response;
        if (metrics != null) {
            response = CompletableFuture.completedFuture(metrics);
        } else {
            // TODO(brandon): Investigate refreshing eagerly or in the background
            // Refresh the cache
            final CompletionStage<List<String>> queryResponse = _kairosDbClient.queryMetricNames()
                    .thenApply(KairosMetricNamesQueryResponse::getResults)
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

    private static MetricsQuery useAvailableRollups(final List<String> metricNames, final MetricsQuery originalQuery) {
        final MetricsQuery.Builder newQueryBuilder = new MetricsQuery.Builder()
                .setStartTime(originalQuery.getStartTime());

        originalQuery.getEndTime().ifPresent(newQueryBuilder::setEndTime);


        newQueryBuilder.setMetrics(originalQuery.getMetrics().stream().map(metric -> {
            // Check to see if there are any rollups for this metrics
            final String metricName = metric.getName();
            if (metricName.endsWith("_!")) {
                // Special case a _! suffix to not apply rollup selection
                // Drop the suffix and forward the request
                return Metric.Builder.fromMetric(metric)
                        .setName(metricName.substring(0, metricName.length() - 2))
                        .build();
            } else {
                final ImmutableList<String> filteredMetrics = filterMetricNames(metricNames, Optional.of(metricName), false);
                final List<String> rollupMetrics = filteredMetrics
                        .stream()
                        .filter(IS_ROLLUP)
                        .filter(s -> s.length() == metricName.length() + 3)
                        .collect(Collectors.toList());

                if (rollupMetrics.isEmpty()) {
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
                            final Optional<SamplingUnit> rollupUnit = rollupSuffixToSamplingUnit(name.substring(metricName.length() + 1));
                            rollupUnit.ifPresent(samplingUnit -> orderedRollups.put(samplingUnit, name));
                        });

                        final Map.Entry<SamplingUnit, String> floorEntry = orderedRollups.floorEntry(maxUnit.get());
                        final String rollupName = floorEntry != null ? floorEntry.getValue() : metricName;
                        final Metric.Builder metricBuilder = Metric.Builder.fromMetric(metric)
                                .setName(rollupName);

                        return metricBuilder.build();
                    }

                    return metric;
                }
            }
        })
                .collect(ImmutableList.toImmutableList()));


        return newQueryBuilder.build();
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

    private final KairosDbClient _kairosDbClient;
    private final Cache<String, List<String>> _cache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();
    private final AtomicReference<List<String>> _metricsList = new AtomicReference<>(null);
    private static final String METRICS_KEY = "METRICNAMES";
    private static final Predicate<String> IS_PT1M = s -> s.startsWith("PT1M/");
    private static final Predicate<String> IS_ROLLUP = s -> s.endsWith("_1h") || s.endsWith("_1d");
}
