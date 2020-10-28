/*
 * Copyright 2020 Dropbox, Inc.
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

package com.arpnetworking.metrics.portal.query.impl;

import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.arpnetworking.kairos.service.KairosDbService;
import com.arpnetworking.metrics.portal.query.QueryExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import models.internal.BoundedMetricsQuery;
import models.internal.MetricsQuery;
import models.internal.MetricsQueryFormat;
import models.internal.MetricsQueryResult;
import models.internal.TimeSeriesResult;
import models.internal.impl.DefaultMetricsQueryResult;
import models.internal.impl.DefaultTimeSeriesResult;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;

/**
 * A {@code QueryExecutor} that accepts KairosDB JSON metrics queries.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class KairosDbQueryExecutor implements QueryExecutor {
    private final KairosDbService _service;
    private final ObjectMapper _objectMapper;

    /**
     * Default Constructor.
     *
     * @param service The KairosDBService used to execute queries.
     * @param objectMapper The objectMapper used to parse queries.
     */
    @Inject
    public KairosDbQueryExecutor(final KairosDbService service, final ObjectMapper objectMapper) {
        _service = service;
        _objectMapper = objectMapper;
    }

    @Override
    public CompletionStage<MetricsQueryResult> executeQuery(final BoundedMetricsQuery query) {
        try {
            return executeQueryInner(query);
            /* CHECKSTYLE.OFF: IllegalCatch - Exception is propagated into the CompletionStage */
        } catch (final Exception e) {
            /* CHECKSTYLE.ON: IllegalCatch */
            final CompletableFuture<MetricsQueryResult> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    @Override
    public Optional<ChronoUnit> periodHint(final MetricsQuery query) {
        assertFormatIsSupported(query.getQueryFormat());
        final com.arpnetworking.kairos.client.models.MetricsQuery metricsQuery;
        try {
            metricsQuery = _objectMapper.readValue(query.getQuery(),
                    com.arpnetworking.kairos.client.models.MetricsQuery.class);
        } catch (final IOException e) {
            throw new RuntimeException("Could not parse query", e);
        }
        // The period hint of the query is the smallest of each metric within
        return metricsQuery.getMetrics()
                .stream()
                .map(this::periodHint)
                .flatMap(Streams::stream)
                .min(ChronoUnit::compareTo);
    }

    private Optional<ChronoUnit> periodHint(final Metric metric) {
        // The period hint is the coarsest aggregator used anywhere in the chain.
        return metric.getAggregators()
                .stream()
                .flatMap(agg -> Streams.stream(agg.getSampling()))
                .map(sampling -> SamplingUnit.toChronoUnit(sampling.getUnit()))
                .max(ChronoUnit::compareTo);
    }

    private CompletionStage<MetricsQueryResult> executeQueryInner(final BoundedMetricsQuery query) {
        assertFormatIsSupported(query.getQueryFormat());
        final com.arpnetworking.kairos.client.models.MetricsQuery.Builder metricsQueryBuilder;
        try {
            metricsQueryBuilder = _objectMapper.readValue(query.getQuery(),
                    com.arpnetworking.kairos.client.models.MetricsQuery.Builder.class);
        } catch (final IOException e) {
            throw new RuntimeException("Could not parse query", e);
        }
        metricsQueryBuilder.setStartTime(query.getStartTime().toInstant());
        query.getEndTime().ifPresent(endTime ->
                metricsQueryBuilder.setEndTime(endTime.toInstant())
        );
        final com.arpnetworking.kairos.client.models.MetricsQuery metricsQuery = metricsQueryBuilder.build();
        // TODO(cbriones):
        // This will not propagate the structured error information from KairosDB
        // until _service provides that capability.
        //
        // However, since the service call will still resolve with an exception
        // this is mostly an issue of debuggability.
        return _service.queryMetrics(metricsQuery).thenApply(this::toInternal);
    }

    private MetricsQueryResult toInternal(final MetricsQueryResponse kairosDbResult) {
        final ImmutableList<TimeSeriesResult.Query> queries = kairosDbResult.getQueries()
                .stream()
                .map(KairosDbQueryExecutor::toInternal)
                .collect(ImmutableList.toImmutableList());

        final TimeSeriesResult timeSeriesResult = new DefaultTimeSeriesResult.Builder()
            .setQueries(queries)
            .build();

        return new DefaultMetricsQueryResult.Builder()
            .setQueryResult(timeSeriesResult)
            .build();
    }

    private static TimeSeriesResult.Query toInternal(final MetricsQueryResponse.Query query) {
        final ImmutableList<TimeSeriesResult.Result> results = query.getResults().stream()
                .map(KairosDbQueryExecutor::toInternal)
                .collect(ImmutableList.toImmutableList());

        return new DefaultTimeSeriesResult.Query.Builder()
                .setResults(results)
                .setSampleSize(query.getSampleSize())
                .build();
    }

    private static TimeSeriesResult.Result toInternal(final MetricsQueryResponse.QueryResult qr) {
        final ImmutableList<TimeSeriesResult.QueryGroupBy> groupBys =
                qr.getGroupBy()
                    .stream()
                    .map(MetricsQueryResponse.QueryGroupBy::toInternal)
                    .collect(ImmutableList.toImmutableList());

        final ImmutableList<TimeSeriesResult.DataPoint> values =
                qr.getValues()
                    .stream()
                    .map(v -> new DefaultTimeSeriesResult.DataPoint.Builder()
                            .setTime(v.getTime())
                            .setValue(v.getValue()).build())
                    .collect(ImmutableList.toImmutableList());

        return new DefaultTimeSeriesResult.Result.Builder()
                .setName(qr.getName())
                .setTags(qr.getTags())
                .setGroupBy(groupBys)
                .setValues(values)
                .build();
    }

    private void assertFormatIsSupported(final MetricsQueryFormat queryFormat) {
        if (!queryFormat.equals(MetricsQueryFormat.KAIROS_DB)) {
            throw new UnsupportedOperationException("Unsupported query format: " + queryFormat);
        }
    }
}
