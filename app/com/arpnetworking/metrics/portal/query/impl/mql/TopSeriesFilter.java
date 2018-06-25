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
                    .getQueryResult()
                    .getQueries()
                    .stream()
                            .map(MetricsQueryResult.Query::getResults)
                            .flatMap(List::stream)
                            .map(QueryScore::new)
                    .collect(Collectors.toList()));
        }
        while (scores.size() > _count) {
            scores.poll();
        }
        final MetricsQueryResult.Query newQueries = new DefaultMetricsQueryResult.Query.Builder()
                .setResults(
                        scores.stream()
                                .map(QueryScore::getQuery)
                                .collect(ImmutableList.toImmutableList()))
                .build();
        final MetricsQueryResult newResponse = new DefaultMetricsQueryResult.Builder().setQueries(ImmutableList.of(newQueries)).build();
        return CompletableFuture.completedFuture(new DefaultTimeSeriesResult.Builder().setQueryResult(newResponse).build());
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
        QueryScore(final MetricsQueryResult.Result query) {
            _query = query;
        }

        public double getScore() {
            if (_score == null) {
                _score = scoreQuery();
            }
            return _score;
        }

        public MetricsQueryResult.Result getQuery() {
            return _query;
        }

        private double scoreQuery() {
             return _query.getValues().stream()
                     .map(MetricsQueryResult.DataPoint::getValue)
                     .map(a -> (Number) a)
                     .mapToDouble(Number::doubleValue)
                     .sum();
        }

        private final MetricsQueryResult.Result _query;
        private Double _score;
    }
}
