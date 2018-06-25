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

import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MoreCollectors;
import models.internal.AlertTrigger;
import models.internal.MetricsQueryResult;
import models.internal.TimeSeriesResult;
import models.internal.impl.DefaultAlertTrigger;
import models.internal.impl.DefaultMetricsQueryResult;
import models.internal.impl.DefaultTimeSeriesResult;
import net.sf.oval.constraint.NotNull;
import org.joda.time.DateTime;
import org.joda.time.Period;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Base class to use for building alert executions.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public abstract class BaseAlertExecution extends BaseExecution {

    @Override
    public CompletionStage<TimeSeriesResult> executeWithDependencies(final Map<StageExecution, TimeSeriesResult> results) {


        final ImmutableList<MetricsQueryResult.Query> queries = dependencies().stream()
                .map(results::get)
                .flatMap(this::evaluateTimeSeries)
                .collect(ImmutableList.toImmutableList());

        final MetricsQueryResult newResponse = new DefaultMetricsQueryResult.Builder().setQueries(queries).build();
        return CompletableFuture.completedFuture(new DefaultTimeSeriesResult.Builder().setQueryResult(newResponse).build());
    }

    public Period getRecoveryPeriod() {
        return _recoveryPeriod;
    }

    public Period getDwellPeriod() {
        return _dwellPeriod;
    }


    /**
     * Evaluates a {@link TimeSeriesResult} and returns a stream of Queries with {@link AlertTrigger}s applied.
     *
     * @param series an input TimeSeriesResult
     * @return a stream of Query with alerts applied
     */
    protected Stream<MetricsQueryResult.Query> evaluateTimeSeries(final TimeSeriesResult series) {
        return series.getQueryResult().getQueries().stream()
                .map(this::evaluateQuery);
    }

    /**
     * Evaluates a {@link MetricsQueryResponse.Query} and returns a new Query with {@link AlertTrigger}s applied.
     *
     * @param query an input Query
     * @return a new Query with alerts applied
     */
    protected MetricsQueryResult.Query evaluateQuery(final MetricsQueryResult.Query query) {
        final DefaultMetricsQueryResult.Query.Builder clone = DefaultMetricsQueryResult.Query.Builder.clone(query);
        final ImmutableList<? extends MetricsQueryResult.Result> newResults = query.getResults()
                .stream()
                .map(this::evaluateQueryResult)
                .collect(ImmutableList.toImmutableList());
        clone.setResults(newResults);
        return clone.build();
    }

    /**
     * Evaluates the {@link AlertTrigger}s on a {@link MetricsQueryResponse.QueryResult}.
     * Usually called for each {@link MetricsQueryResponse.QueryResult} returned in the
     * {@link MetricsQueryResponse.Query} from {@link BaseAlertExecution#evaluateQuery(MetricsQueryResult.Query)}.
     * Override this function if the alert contains logic that takes into account history in a time series.
     *
     * NOTE: the default implementation takes into account alert state and recovery time.
     *
     * @param result a {@link MetricsQueryResponse.QueryResult}
     * @return a {@link Stream} of {@link AlertTrigger}
     */
    // CHECKSTYLE.OFF: ExecutableStatementCount - Well, it's just complicated
    protected MetricsQueryResult.Result evaluateQueryResult(final MetricsQueryResult.Result result) {
        final ImmutableList.Builder<AlertTrigger> alerts = ImmutableList.<AlertTrigger>builder().addAll(result.getAlerts());

        final ImmutableList<? extends MetricsQueryResult.DataPoint> values = result.getValues();
        int x = 0;

        DateTime breachStart = null;
        DateTime breachLast = null;
        DefaultAlertTrigger.Builder alertBuilder = null;
        // We'll lazy create this
        final Supplier<ImmutableMap<String, String>> args = Suppliers.memoize(() -> createArgs(result));
        final Supplier<ImmutableMap<String, String>> groupBy = Suppliers.memoize(() -> createGroupBy(result));

        while (x < values.size()) {
            // If we have an alert
            final MetricsQueryResult.DataPoint dataPoint = values.get(x);
            if (evaluateDataPoint(dataPoint)) {
                if (breachStart == null) {
                    breachStart = dataPoint.getTime();
                }
                if (!dataPoint.getTime().minus(_dwellPeriod).isBefore(breachStart)) {
                    // Don't start a new alert unless we are in an OK period
                    if (alertBuilder == null) {
                        alertBuilder = new DefaultAlertTrigger.Builder().setTime(dataPoint.getTime()).setArgs(args.get());
                        alertBuilder.setMessage(getMessage(dataPoint));
                        alertBuilder.setGroupBy(groupBy.get());
                    }
                    // Consume the range of in-alert points
                    breachLast = dataPoint.getTime();
                    while (x < values.size() && evaluateDataPoint(values.get(x))) {
                        breachLast = values.get(x).getTime();
                        x++;
                    }
                    x--;
                }
            } else {
                // Check to see if we're far enough past the last bad sample to clear the alert
                if (breachLast != null && breachLast.plus(_recoveryPeriod).isBefore(dataPoint.getTime())) {
                    alertBuilder.setEndTime(Optional.of(dataPoint.getTime()));
                    alerts.add(alertBuilder.build());
                    alertBuilder = null;
                    breachLast = null;
                }
                breachStart = null;
            }
            x++;
        }
        // If we still have an alertBuilder, the alert is ongoing, set the end time to the final sample
        if (alertBuilder != null) {
            alertBuilder.setEndTime(Optional.of(values.get(values.size() - 1).getTime()));
            alerts.add(alertBuilder.build());
        }

        final DefaultMetricsQueryResult.Result.Builder newResult = DefaultMetricsQueryResult.Result.Builder.clone(result);
        newResult.setAlerts(alerts.build());
        return newResult.build();
    }
    // CHECKSTYLE.ON: ExecutableStatementCount

    /**
     * Gets a friendly message indicating why a datapoint is in alert.
     *
     * @param dataPoint Datapoint out of spec
     * @return a message indicating the alert reason
     */
    protected String getMessage(final MetricsQueryResult.DataPoint dataPoint) {
        return String.format("value of %s at %s was out of expected range", getDataPointValue(dataPoint), dataPoint.getTime());
    }
    /**
     * Gets a friendly formatting of the data point value.
     *
     * @param dataPoint Datapoint out of spec
     * @return a message indicating the alert reason
     */
    protected String getDataPointValue(final MetricsQueryResult.DataPoint dataPoint) {
        final DecimalFormat df = new DecimalFormat("0");
        df.setMaximumFractionDigits(340);
        return df.format(dataPoint.getValue());
    }

    private ImmutableMap<String, String> createArgs(final MetricsQueryResult.Result result) {
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

    private ImmutableMap<String, String> createGroupBy(final MetricsQueryResult.Result result) {
        final ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        result.getGroupBy()
                .stream()
                .filter(MetricsQueryResult.QueryTagGroupBy.class::isInstance)
                .map(MetricsQueryResponse.QueryTagGroupBy.class::cast)
                .map(MetricsQueryResponse.QueryTagGroupBy::getGroup)
                .map(ImmutableMap::entrySet)
                .flatMap(Collection::stream)
                .forEach(entry -> builder.put(entry.getKey(), entry.getValue()));
        return builder.build();
    }

    /**
     * Evaluates whether a data point is out of tolerance. Usually called by
     * {@link BaseAlertExecution#evaluateQueryResult(MetricsQueryResult.Result)}.
     * Override this function if the alert is stateless and based on individual values.
     *
     * @param dataPoint the datapoint in question
     * @return true if the datapoint is in alert, false if OK
     */
    protected boolean evaluateDataPoint(final MetricsQueryResult.DataPoint dataPoint) {
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
        _dwellPeriod = builder._dwellPeriod;
    }

    private final Period _recoveryPeriod;
    private final Period _dwellPeriod;

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

        /**
         * Sets the dwell period. The threshold conditions must be met for this period of time before an alert is made.
         * Optional. Cannot be null.
         *
         * @param value the recovery period
         * @return this {@link Builder}
         */
        B setDwellPeriod(final Period value) {
            _dwellPeriod = value;
            return self();
        }

        @NotNull
        private Period _recoveryPeriod = Period.minutes(0);

        @NotNull
        private Period _dwellPeriod = Period.minutes(0);
    }
}
