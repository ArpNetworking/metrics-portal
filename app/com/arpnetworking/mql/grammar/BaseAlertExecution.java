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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MoreCollectors;
import net.sf.oval.constraint.NotNull;
import org.joda.time.DateTime;
import org.joda.time.Period;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Base class to use for building alert executions.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public abstract class BaseAlertExecution extends BaseExecution {

    @Override
    public CompletionStage<TimeSeriesResult> executeWithDependencies(final Map<StageExecution, TimeSeriesResult> results) {


        final ImmutableList<MetricsQueryResponse.Query> queries = dependencies().stream()
                .map(results::get)
                .flatMap(this::evaluateTimeSeries)
                .collect(ImmutableCollectors.toList());

        final MetricsQueryResponse newResponse = new MetricsQueryResponse.Builder().setQueries(queries).build();
        return CompletableFuture.completedFuture(new TimeSeriesResult.Builder().setResponse(newResponse).build());
    }

    /**
     * Evaluates a {@link TimeSeriesResult} and returns a stream of Queries with {@link AlertTrigger}s applied.
     *
     * @param series an input TimeSeriesResult
     * @return a stream of Query with alerts applied
     */
    protected Stream<MetricsQueryResponse.Query> evaluateTimeSeries(final TimeSeriesResult series) {
        return series.getResponse().getQueries().stream()
                .map(this::evaluateQuery);
    }

    /**
     * Evaluates a {@link MetricsQueryResponse.Query} and returns a new Query with {@link AlertTrigger}s applied.
     *
     * @param query an input Query
     * @return a new Query with alerts applied
     */
    protected MetricsQueryResponse.Query evaluateQuery(final MetricsQueryResponse.Query query) {
        final MetricsQueryResponse.Query.Builder clone = MetricsQueryResponse.Query.Builder.clone(query);
        final ImmutableList<MetricsQueryResponse.QueryResult> newResults = query.getResults()
                .stream()
                .map(this::evaluateQueryResult)
                .collect(ImmutableCollectors.toList());
        clone.setResults(newResults);
        return clone.build();
    }

    /**
     * Evaluates the {@link AlertTrigger}s on a {@link MetricsQueryResponse.QueryResult}.
     * Usually called for each {@link MetricsQueryResponse.QueryResult} returned in the
     * {@link MetricsQueryResponse.Query} from {@link BaseAlertExecution#evaluateQuery(MetricsQueryResponse.Query)}.
     * Override this function if the alert contains logic that takes into account history in a time series.
     *
     * NOTE: the default implementation takes into account alert state and recovery time.
     *
     * @param result a {@link MetricsQueryResponse.QueryResult}
     * @return a {@link Stream} of {@link AlertTrigger}
     */
    protected MetricsQueryResponse.QueryResult evaluateQueryResult(final MetricsQueryResponse.QueryResult result) {
        final ImmutableList.Builder<AlertTrigger> alerts = ImmutableList.builder();

        final List<MetricsQueryResponse.DataPoint> values = result.getValues();
        int x = 0;

        DateTime last = null;
        AlertTrigger.Builder alertBuilder = null;
        // We'll lazy create this
        ImmutableMap<String, String> args = null;
        while (x < values.size()) {
            // If we have an alert
            final MetricsQueryResponse.DataPoint dataPoint = values.get(x);
            if (evaluateDataPoint(dataPoint)) {
                // Don't start a new alert unless we are in an OK period
                if (alertBuilder == null) {
                    if (args == null) {
                        args = createArgs(result);
                    }
                    alertBuilder = new AlertTrigger.Builder().setTime(dataPoint.getTime()).setArgs(args);
                }
                // Consume the range of in-alert points
                last = dataPoint.getTime();
                while (x < values.size() && evaluateDataPoint(values.get(x))) {
                    last = values.get(x).getTime();
                    x++;
                }
            } else {
                // Check to see if we're far enough past the last bad sample to clear the alert
                if (last != null && last.plus(_recoveryPeriod).isBefore(dataPoint.getTime())) {
                    alertBuilder.setEndTime(dataPoint.getTime());
                    alerts.add(alertBuilder.build());
                    alertBuilder = null;
                    last = null;
                }
                x++;
            }
        }
        // If we still have an alertBuilder, the alert is ongoing, set the end time to the final sample
        if (alertBuilder != null) {
            alertBuilder.setEndTime(values.get(values.size() - 1).getTime());
            alerts.add(alertBuilder.build());
        }

        final MetricsQueryResponse.QueryResult.Builder newResult = MetricsQueryResponse.QueryResult.Builder.clone(result);
        newResult.setAlerts(alerts.build());
        return newResult.build();
    }

    private ImmutableMap<String, String> createArgs(final MetricsQueryResponse.QueryResult result) {
        final ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        builder.put("name", result.getName());
        result.getTags()
                .asMap()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().size() == 1)
                .forEach(entry -> builder.put(entry.getKey(), entry.getValue().stream().collect(MoreCollectors.onlyElement())));
        return builder.build();
    }

    /**
     * Evaluates whether a data point is out of tolerance. Usually called by
     * {@link BaseAlertExecution#evaluateQueryResult(MetricsQueryResponse.QueryResult)}.
     * Override this function if the alert is stateless and based on individual values.
     *
     * @param dataPoint the datapoint in question
     * @return true if the datapoint is in alert, false if OK
     */
    protected boolean evaluateDataPoint(final MetricsQueryResponse.DataPoint dataPoint) {
        return false;
    }

    /**
     * Protected constructor.
     *
     * @param builder the builder
     */
    protected BaseAlertExecution(final Builder<?, ?> builder) {
        super(builder);
        _recoveryPeriod = builder._recoveryPeriod;
    }

    private final Period _recoveryPeriod;

    /**
     * Implementation of the Builder pattern for {@link BaseAlertExecution}.
     *
     * @param <B> type of the Builder
     * @param <E> type of the AlertExecution
     */
    public abstract static class Builder<B extends Builder<B, E>, E extends BaseAlertExecution> extends BaseExecution.Builder<B, E> {
        /**
         * Protected constructor.
         *
         * @param targetConstructor constructor for the Execution
         */
        protected Builder(final Function<B, E> targetConstructor) {
            super(targetConstructor);
        }


        /**
         * Sets the recovery period.  After this period of OK, the alert is considered recovered. Optional. Cannot be null.
         *
         * @param value the recovery period
         * @return this {@link Builder}
         */
        B setRecoveryPeriod(final Period value) {
            _recoveryPeriod = value;
            return self();
        }

        @NotNull
        private Period _recoveryPeriod = Period.minutes(0);
    }
}
