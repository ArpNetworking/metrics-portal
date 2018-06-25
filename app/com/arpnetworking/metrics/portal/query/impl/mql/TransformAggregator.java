/**
 * Copyright 2017 Smartsheet
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
package com.arpnetworking.metrics.portal.query.impl.mql;

import com.google.common.collect.ImmutableList;
import models.internal.MetricsQueryResult;
import models.internal.TimeSeriesResult;
import models.internal.impl.DefaultMetricsQueryResult;
import models.internal.impl.DefaultTimeSeriesResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

/**
 * Serves as a base for aggregators that transform data.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public abstract class TransformAggregator extends BaseExecution {
    @Override
    public CompletionStage<TimeSeriesResult> executeWithDependencies(final Map<StageExecution, TimeSeriesResult> results) {
        final ImmutableList<MetricsQueryResult.Query> queries = dependencies().stream()
                .map(results::get)
                .flatMap(this::transformTimeSeries)
                .collect(ImmutableList.toImmutableList());

        final MetricsQueryResult newResponse = new DefaultMetricsQueryResult.Builder().setQueries(queries).build();
        return CompletableFuture.completedFuture(new DefaultTimeSeriesResult.Builder().setQueryResult(newResponse).build());
    }

    /**
     * Transforms the {@link TimeSeriesResult} part of the response.
     *
     * @param series the time series result
     * @return a stream of transformed Query objects
     */
    protected Stream<MetricsQueryResult.Query> transformTimeSeries(final TimeSeriesResult series) {
        return series.getQueryResult().getQueries().stream()
                .map(this::transformQuery);
    }

    /**
     * Transforms the {@link com.arpnetworking.kairos.client.models.MetricsQueryResponse.Query} part of the response.
     *
     * @param query the query
     * @return a new, transformed query
     */
    protected MetricsQueryResult.Query transformQuery(final MetricsQueryResult.Query query) {
        final DefaultMetricsQueryResult.Query.Builder clone = DefaultMetricsQueryResult.Query.Builder.clone(query);
        final ImmutableList<? extends MetricsQueryResult.Result> newResults = query.getResults()
                .stream()
                .map(this::transformQueryResult)
                .collect(ImmutableList.toImmutableList());
        clone.setResults(newResults);
        return clone.build();
    }

    /**
     * Transforms the query result.
     *
     * @param result the original query result
     * @return a new, transformed query result
     */
    protected abstract MetricsQueryResult.Result transformQueryResult(MetricsQueryResult.Result result);

    /**
     * Protected constructor.
     *
     * @param builder the builder
     */
    protected TransformAggregator(final BaseExecution.Builder<?, ?> builder) {
        super(builder);
    }
}
