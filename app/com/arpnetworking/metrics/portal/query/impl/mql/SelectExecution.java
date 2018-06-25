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

import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.google.common.collect.ImmutableList;
import models.internal.MetricsQueryResult;
import models.internal.TimeSeriesResult;
import models.internal.impl.DefaultMetricsQueryResult;
import models.internal.impl.DefaultTimeSeriesResult;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Represents an execution of a metrics SELECT query.  Holds incoming references and binding name.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class SelectExecution extends BaseExecution {
    @Override
    public CompletionStage<TimeSeriesResult> executeWithDependencies(final Map<StageExecution, TimeSeriesResult> results) {
        return _client.queryMetrics(_query)
                .thenApply(SelectExecution::mapTimeSeriesResultToInternal)
                .thenApply(result -> new DefaultTimeSeriesResult.Builder().setQueryResult(result).build());
    }

    private static MetricsQueryResult mapTimeSeriesResultToInternal(final MetricsQueryResponse metricsQueryResponse) {
        return new DefaultMetricsQueryResult.Builder()
                .setQueries(mapQueries(metricsQueryResponse.getQueries()))
                .build();
    }

    private static ImmutableList<? extends MetricsQueryResult.Query> mapQueries(final ImmutableList<MetricsQueryResponse.Query> queries) {
        return queries.stream()
                .map(SelectExecution::mapQuery)
                .collect(ImmutableList.toImmutableList());
    }

    private static MetricsQueryResult.Query mapQuery(final MetricsQueryResponse.Query query) {
        return new DefaultMetricsQueryResult.Query.Builder()
                .setSampleSize(query.getSampleSize())
                .setResults(mapResults(query.getResults()))
                .build();
    }

    private static ImmutableList<? extends MetricsQueryResult.Result> mapResults(
            final ImmutableList<MetricsQueryResponse.QueryResult> results) {
        return results.stream()
                .map(SelectExecution::mapResult)
                .collect(ImmutableList.toImmutableList());
    }

    private static MetricsQueryResult.Result mapResult(final MetricsQueryResponse.QueryResult queryResult) {
        return new DefaultMetricsQueryResult.Result.Builder()
                .setName(queryResult.getName())
                .setTags(queryResult.getTags())
                .setValues(mapValues(queryResult.getValues()))
                .setGroupBy(mapGroupBys(queryResult.getGroupBy()))
                .build();
    }

    private static ImmutableList<? extends MetricsQueryResult.QueryGroupBy> mapGroupBys(
            final ImmutableList<MetricsQueryResponse.QueryGroupBy> groupBy) {
        return groupBy.stream()
                .map(SelectExecution::mapGroupBy)
                .collect(ImmutableList.toImmutableList());
    }

    private static MetricsQueryResult.QueryGroupBy mapGroupBy(final MetricsQueryResponse.QueryGroupBy queryGroupBy) {
        if (queryGroupBy instanceof MetricsQueryResponse.QueryTypeGroupBy) {
            return new DefaultMetricsQueryResult.QueryTypeGroupBy.Builder()
                    .setType(((MetricsQueryResponse.QueryTypeGroupBy) queryGroupBy).getType())
                    .build();
        } else if (queryGroupBy instanceof MetricsQueryResponse.QueryTagGroupBy) {
            final MetricsQueryResponse.QueryTagGroupBy tagGroupBy = (MetricsQueryResponse.QueryTagGroupBy) queryGroupBy;
            return new DefaultMetricsQueryResult.QueryTagGroupBy.Builder()
                    .setGroup(tagGroupBy.getGroup())
                    .setTags(tagGroupBy.getTags())
                    .build();
        } else {
            throw new IllegalArgumentException("Unknown GroupBy type " + queryGroupBy.getClass());
        }
    }

    private static ImmutableList<? extends MetricsQueryResult.DataPoint> mapValues(
            final ImmutableList<MetricsQueryResponse.DataPoint> values) {
        return values.stream()
                .map(SelectExecution::mapValue)
                .collect(ImmutableList.toImmutableList());
    }

    private static MetricsQueryResult.DataPoint mapValue(final MetricsQueryResponse.DataPoint dataPoint) {
        return new DefaultMetricsQueryResult.DataPoint.Builder()
                .setTime(dataPoint.getTime())
                .setValue(dataPoint.getValue())
                .build();
    }


    private SelectExecution(final Builder builder) {
        super(builder);
        _query = builder._query;
        _client = builder._client;
    }

    public MetricsQuery getQuery() {
        return _query;
    }

    public KairosDbClient getClient() {
        return _client;
    }

    private final MetricsQuery _query;
    private final KairosDbClient _client;

    /**
     * Implementation of the Builder pattern for a {@link SelectExecution}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends BaseExecution.Builder<Builder, SelectExecution> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(SelectExecution::new);
        }

        /**
         * Sets the KairosDB client.
         *
         * @param value the client
         * @return this {@link Builder}
         */
        public Builder setClient(final KairosDbClient value) {
            _client = value;
            return this;
        }

        /**
         * Sets the query builder.
         *
         * @param value the builder.
         * @return this {@link Builder}
         */
        public Builder setQuery(final MetricsQuery value) {
            _query = value;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        private MetricsQuery _query;
        private KairosDbClient _client;
    }
}
