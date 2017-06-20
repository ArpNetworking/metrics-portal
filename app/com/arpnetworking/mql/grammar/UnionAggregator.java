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
import com.google.common.collect.ImmutableList;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Unions TimeSeriesResults for output.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class UnionAggregator extends BaseExecution {
    @Override
    public CompletionStage<TimeSeriesResult> executeWithDependencies(final Map<StageExecution, TimeSeriesResult> results) {
        final ImmutableList.Builder<MetricsQueryResponse.Query> queries = ImmutableList.builder();
        for (final StageExecution execution : dependencies()) {
            queries.addAll(results.get(execution).getResponse().getQueries());
        }
        final MetricsQueryResponse newResponse = new MetricsQueryResponse.Builder().setQueries(queries.build()).build();
        return CompletableFuture.completedFuture(new TimeSeriesResult.Builder().setResponse(newResponse).build());
    }

    private UnionAggregator(final Builder builder) {
        super(builder);
    }

    /**
     * Implementation of the Builder pattern for {@link UnionAggregator}.
     */
    public static final class Builder extends BaseExecution.Builder<Builder, UnionAggregator> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(UnionAggregator::new);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
