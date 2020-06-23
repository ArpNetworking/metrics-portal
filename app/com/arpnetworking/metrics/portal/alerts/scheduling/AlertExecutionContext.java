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

package com.arpnetworking.metrics.portal.alerts.scheduling;

import com.arpnetworking.metrics.portal.query.QueryExecutor;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import models.internal.BoundedMetricsQuery;
import models.internal.MetricsQuery;
import models.internal.MetricsQueryResult;
import models.internal.Problem;
import models.internal.TimeSeriesResult;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.impl.DefaultAlertEvaluationResult;
import models.internal.impl.DefaultBoundedMetricsQuery;
import models.internal.scheduling.Job;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;

/**
 * Utility class for scheduling and evaluating alerts.
 *
 * This essentially encapsulates the dependencies and functionality necessary to
 * allow {@link Alert} instances to implement the {@link Job} interface.
 * <p>
 * {@code AlertJob} acts as a binding between Alert instances and this class.
 *
 * @see AlertJob
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class AlertExecutionContext {
    private static final String PROBLEM_UNEXPECTED_RESULT = "alert_problem.UNEXPECTED_RESULT";
    private static final String PROBLEM_QUERY_RETURNED_ERRORS = "alert_problem.QUERY_RETURNED_ERRORS";

    private final QueryExecutor _executor;
    private final Schedule _defaultSchedule;

    /**
     * Default constructor.
     *
     * @param defaultSchedule The default alert execution schedule.
     * @param executor The executor to use for alert queries.
     */
    @Inject
    public AlertExecutionContext(
            final Schedule defaultSchedule,
            final QueryExecutor executor
    ) {
        _defaultSchedule = defaultSchedule;
        _executor = executor;
    }

    /**
     * Get an evaluation schedule for this alert.
     * <p>
     * This will attempt to find the largest possible schedule that will still
     * guarantee alert evaluation will not fall behind.
     * <p>
     * If this is not possible, then a default execution interval will be used.
     *
     * @param alert The alert.
     * @return a schedule
     */
    public Schedule getSchedule(final Alert alert) {
        return _defaultSchedule;
    }

    /**
     * Evaluate an alert that was scheduled for the given instant.
     *
     * @param alert The alert to evaluate.
     * @param scheduled The scheduled evaluation time.
     * @return A completion stage containing {@code AlertEvaluationResult}.
     */
    public CompletionStage<AlertEvaluationResult> execute(final Alert alert, final Instant scheduled) {
        try {
            // If we're unable to obtain a period hint then we will not be able to
            // correctly window the query, as smaller intervals could miss data.
            final MetricsQuery query = alert.getQuery();
            final ChronoUnit period = _executor.periodHint(query)
                    .orElseThrow(() -> new IllegalArgumentException("Unable to obtain period hint for query"));
            final BoundedMetricsQuery bounded = applyTimeRange(query, scheduled, period);

            return _executor.executeQuery(bounded).thenApply(res -> toAlertResult(res, scheduled, period));
            // CHECKSTYLE.OFF: IllegalCatch - Exception is propagated into the CompletionStage
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            final CompletableFuture<AlertEvaluationResult> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    private AlertEvaluationResult toAlertResult(
            final MetricsQueryResult queryResult,
            final Instant scheduled,
            final ChronoUnit period
    ) {

        // Query result preconditions:
        //
        // - There is only ever a single query.
        // - If there are multiple results, each must contain a tag group-by.
        // - Otherwise there is exactly one result, which may or may not contain
        //   a tag group-by.
        // - The name field of each result should be identical, since the only
        //   difference should be in the grouping.
        if (!queryResult.getErrors().isEmpty()) {
            throw newCompletionException(
                    PROBLEM_QUERY_RETURNED_ERRORS,
                    "Query executor completed with error",
                    queryResult.getErrors()
            );
        }

        final ImmutableList<? extends TimeSeriesResult.Query> queries = queryResult.getQueryResult().getQueries();
        if (queries.size() != 1) {
            throw newCompletionException(
                    PROBLEM_UNEXPECTED_RESULT,
                    "Expected exactly one query",
                    ImmutableList.of(queryResult)
            );
        }
        final TimeSeriesResult.Query query = queries.get(0);
        final ImmutableList<? extends TimeSeriesResult.Result> results = query.getResults();
        if (results.isEmpty()) {
            throw newCompletionException(
                    PROBLEM_UNEXPECTED_RESULT,
                    "Expected at least one result",
                    ImmutableList.of(queryResult)
            );
        }

        final long uniqueNames = results.stream()
                .map(TimeSeriesResult.Result::getName)
                .distinct()
                .limit(2)
                .count();

        final String name = results.get(0).getName();
        if (uniqueNames > 1) {
            throw newCompletionException(
                    PROBLEM_UNEXPECTED_RESULT,
                    "All result metric names must be identical",
                    ImmutableList.of(queryResult)
            );
        }

        if (results.size() > 1 && results.stream().anyMatch(res -> !getTagGroup(res).isPresent())) {
            throw newCompletionException(
                    PROBLEM_UNEXPECTED_RESULT,
                    "All results must contain a tag group-by if there are multiple results",
                    ImmutableList.of(queryResult)
            );
        }

        final List<Map<String, String>> firingTagGroups =
                results
                    .stream()
                    .filter(res -> isFiring(res.getValues(), period, scheduled))
                    .map(res -> getTagGroup(res).orElseGet(ImmutableMap::of))
                    .collect(ImmutableList.toImmutableList());

        return new DefaultAlertEvaluationResult.Builder()
                .setName(name)
                .setFiringTags(firingTagGroups)
                .build();
    }

    private boolean isFiring(final List<? extends TimeSeriesResult.DataPoint> values,
                             final ChronoUnit period,
                             final Instant scheduled) {
        if (values.isEmpty()) {
            return false;
        }
        // The most recent datapoint and scheduled time must belong to the same
        // period.
        final Instant mostRecentDatapointTime = values.get(values.size() - 1).getTime();
        return period.between(mostRecentDatapointTime, scheduled) == 0;
    }

    private Optional<Map<String, String>> getTagGroup(final TimeSeriesResult.Result result) {
        // We don't expect the tag group-by to be the only value in the stream
        // because the histogram plugin uses its own group-by.
        return result.getGroupBy().stream()
                .filter(g -> g instanceof TimeSeriesResult.QueryTagGroupBy)
                .map(g -> (TimeSeriesResult.QueryTagGroupBy) g)
                .findFirst()
                .map(TimeSeriesResult.QueryTagGroupBy::getGroup);
    }

    private BoundedMetricsQuery applyTimeRange(final MetricsQuery query,
                                               final Instant scheduled,
                                               final ChronoUnit period) {
        // We must truncate to avoid dealing with partially aggregated periods.
        final ZonedDateTime latest = ZonedDateTime.ofInstant(scheduled, ZoneOffset.UTC).truncatedTo(period);
        final ZonedDateTime previous = latest.minus(1, period);

        return new DefaultBoundedMetricsQuery.Builder()
                .setStartTime(previous)
                .setEndTime(latest)
                .setQuery(query.getQuery())
                .setFormat(query.getQueryFormat())
                .build();
    }

    // Helpers for constructing exceptions.

    private static CompletionException newCompletionException(final String problemCode, final String message) {
        return newCompletionException(problemCode, message, ImmutableList.of());
    }

    private static CompletionException newCompletionException(final String problemCode, final String message, final ImmutableList<?> args) {
        final Problem problem = new Problem.Builder()
                .setProblemCode(problemCode)
                .setArgs(ImmutableList.copyOf(args))
                .build();
        return new CompletionException(
                new AlertExecutionException(message, ImmutableList.of(problem))
        );
    }
}
