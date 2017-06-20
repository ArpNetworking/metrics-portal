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
package com.arpnetworking.mql.grammar;

import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.metrics.util.ImmutableCollectors;
import com.google.common.collect.ImmutableList;

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
        final ImmutableList<MetricsQueryResponse.Query> queries = dependencies().stream()
                .map(results::get)
                .flatMap(this::transformTimeSeries)
                .collect(ImmutableCollectors.toList());

        final MetricsQueryResponse newResponse = new MetricsQueryResponse.Builder().setQueries(queries).build();
        return CompletableFuture.completedFuture(new TimeSeriesResult.Builder().setResponse(newResponse).build());
    }

    /**
     * Transforms the {@link TimeSeriesResult} part of the response.
     *
     * @param series the time series result
     * @return a stream of transformed Query objects
     */
    protected Stream<MetricsQueryResponse.Query> transformTimeSeries(final TimeSeriesResult series) {
        return series.getResponse().getQueries().stream()
                .map(this::transformQuery);
    }

    /**
     * Transforms the {@link com.arpnetworking.kairos.client.models.MetricsQueryResponse.Query} part of the response.
     *
     * @param query the query
     * @return a new, transformed query
     */
    protected MetricsQueryResponse.Query transformQuery(final MetricsQueryResponse.Query query) {
        final MetricsQueryResponse.Query.Builder clone = MetricsQueryResponse.Query.Builder.clone(query);
        final ImmutableList<MetricsQueryResponse.QueryResult> newResults = query.getResults()
                .stream()
                .map(this::transformQueryResult)
                .collect(ImmutableCollectors.toList());
        clone.setResults(newResults);
        return clone.build();
    }

    /**
     * Transforms the query result.
     *
     * @param result the original query result
     * @return a new, transformed query result
     */
    protected abstract MetricsQueryResponse.QueryResult transformQueryResult(MetricsQueryResponse.QueryResult result);

    /**
     * Protected constructor.
     *
     * @param builder the builder
     */
    protected TransformAggregator(final BaseExecution.Builder<?, ?> builder) {
        super(builder);
    }
}
