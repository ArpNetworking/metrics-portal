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
import net.sf.oval.constraint.Min;
import net.sf.oval.constraint.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Execution that filters the top or bottom N series.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class TopSeriesFilter extends BaseExecution {
    @Override
    public CompletionStage<TimeSeriesResult> executeWithDependencies(final Map<StageExecution, TimeSeriesResult> results) {
        final PriorityQueue<QueryScore> scores = new PriorityQueue<>(_count, _comparator);
        for (final StageExecution execution : dependencies()) {
            scores.addAll(
            results.get(execution)
                    .getResponse()
                    .getQueries()
                    .stream()
                            .map(MetricsQueryResponse.Query::getResults)
                            .flatMap(List::stream)
                            .map(QueryScore::new)
                    .collect(Collectors.toList()));
        }
        while (scores.size() > _count) {
            scores.poll();
        }
        final MetricsQueryResponse.Query newQueries = new MetricsQueryResponse.Query.Builder()
                .setResults(
                        scores.stream()
                                .map(QueryScore::getQuery)
                                .collect(ImmutableCollectors.toList()))
                .build();
        final MetricsQueryResponse newResponse = new MetricsQueryResponse.Builder().setQueries(ImmutableList.of(newQueries)).build();
        return CompletableFuture.completedFuture(new TimeSeriesResult.Builder().setResponse(newResponse).build());
    }

    private TopSeriesFilter(final Builder builder) {
        super(builder);
        _count = builder._count;
        if (!builder._invert) {
            _comparator = Comparator.comparing(QueryScore::getScore);
        } else {
            _comparator = Comparator.comparing(QueryScore::getScore).reversed();
        }
    }

    private final Comparator<QueryScore> _comparator;

    private final Integer _count;

    /**
     * Implementation of the Builder pattern for {@link TopSeriesFilter}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends BaseExecution.Builder<Builder, TopSeriesFilter> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(TopSeriesFilter::new);
        }

        @Override
        protected Builder self() {
            return this;
        }

        /**
         * Sets the count of series to filter to. Optional. Cannot be null. Defaults to 1.
         *
         * @param value the number of desired series
         * @return this {@link Builder}
         */
        public Builder setCount(final Integer value) {
            _count = value;
            return this;
        }

        /**
         * Invert to take the bottom instead of the top. Optional. Defaults to false.
         *
         * @param value true to choose the bottom series
         * @return this {@link Builder}
         */
        public Builder setInvert(final Boolean value) {
            _invert = value;
            return this;
        }

        @NotNull
        @Min(1)
        private Integer _count = 1;

        @NotNull
        private Boolean _invert = false;
    }

    private static final class QueryScore {
        QueryScore(final MetricsQueryResponse.QueryResult query) {
            _query = query;
        }

        public double getScore() {
            if (_score == null) {
                _score = scoreQuery();
            }
            return _score;
        }

        public MetricsQueryResponse.QueryResult getQuery() {
            return _query;
        }

        private double scoreQuery() {
             return _query.getValues().stream()
                     .map(MetricsQueryResponse.DataPoint::getValue)
                     .map(a -> (Number) a)
                     .mapToDouble(Number::doubleValue)
                     .sum();
        }

        private final MetricsQueryResponse.QueryResult _query;
        private Double _score;
    }
}
