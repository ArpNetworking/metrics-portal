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

import com.arpnetworking.metrics.portal.alerts.AlertNotifier;
import com.arpnetworking.metrics.portal.query.QueryAlignment;
import com.arpnetworking.metrics.portal.query.QueryExecutor;
import com.arpnetworking.metrics.portal.query.QueryWindow;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import jakarta.inject.Inject;
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

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.stream.IntStream;

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
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    private static final String PROBLEM_UNEXPECTED_RESULT = "alert_problem.UNEXPECTED_RESULT";
    private static final String PROBLEM_QUERY_RETURNED_ERRORS = "alert_problem.QUERY_RETURNED_ERRORS";

    private final QueryExecutor _executor;
    private final Schedule _defaultSchedule;
    private final Duration _queryOffset;
    private final AlertNotifier _alertNotifier;

    /**
     * Default constructor.
     *
     * @param defaultSchedule The default alert execution schedule.
     * @param executor The executor to use for alert queries.
     * @param queryOffset The offset to apply to the query interval.
     * @param alertNotifier The notifier to use to notify users of alert triggers.
     */
    @Inject
    public AlertExecutionContext(
            final Schedule defaultSchedule,
            final QueryExecutor executor,
            final Duration queryOffset,
            final AlertNotifier alertNotifier
    ) {
        _defaultSchedule = defaultSchedule;
        _executor = executor;
        _queryOffset = queryOffset;
        _alertNotifier = alertNotifier;
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
        // QueryExecutor#evaluationPeriodHint could be used here to reduce query
        // frequency if there are a large proportion of period-aligned queries.
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
            final QueryWindow window = _executor.queryWindow(query);
            final BoundedMetricsQuery bounded = applyTimeRange(query, scheduled, window);
            final Instant queryRangeStart = bounded.getStartTime().toInstant();
            final Instant queryRangeEnd =
                    bounded.getEndTime()
                            .orElseThrow(() ->
                                    new IllegalStateException("AlertExecutionContext queries must have an explicit end time")
                            )
                            .toInstant();

            return _executor
                    .executeQuery(bounded)
                    .thenApply(res -> toAlertResult(res, scheduled, window, queryRangeStart, queryRangeEnd))
                    .thenCompose(result -> {
                        if (result.getFiringTags().size() > 0) {
                            return _alertNotifier.notify(alert, result).thenApply(v -> result);
                        } else {
                            return CompletableFuture.completedFuture(result);
                        }
                    });
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
            final QueryWindow window,
            final Instant queryRangeStart,
            final Instant queryRangeEnd
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

        if (results.size() > 1 && results.stream().anyMatch(res -> !getTagGroupBy(res).isPresent())) {
            throw newCompletionException(
                    PROBLEM_UNEXPECTED_RESULT,
                    "All results must contain a tag group-by if there are multiple results",
                    ImmutableList.of(queryResult)
            );
        }

        final List<String> groupBys =
                results.stream()
                    .map(this::getTagGroupBy)
                    .flatMap(Streams::stream)
                    .map(TimeSeriesResult.QueryTagGroupBy::getTags)
                    .findAny()
                    .orElse(ImmutableList.of());

        final List<Map<String, String>> firingTagGroups =
                results
                    .stream()
                    .filter(res -> isFiring(res.getValues(), window.getLookbackPeriod(), scheduled))
                    .map(res ->
                        getTagGroupBy(res)
                            .map(TimeSeriesResult.QueryTagGroupBy::getGroup)
                            .orElseGet(ImmutableMap::of)
                    )
                    .collect(ImmutableList.toImmutableList());

        return new DefaultAlertEvaluationResult.Builder()
                .setSeriesName(name)
                .setGroupBys(groupBys)
                .setFiringTags(firingTagGroups)
                .setQueryStartTime(queryRangeStart)
                .setQueryEndTime(queryRangeEnd)
                .build();
    }

    private boolean isFiring(final List<? extends TimeSeriesResult.DataPoint> values,
                             final Duration period,
                             final Instant scheduled) {
        final Instant adjustedScheduled = scheduled.minus(_queryOffset);

        // Since the query window is the exactly the length of the query period, there will
        // be at most two datapoints per series - one for each query window endpoint.
        //
        // If the lookback period is minutely, then we're safe grabbing the most recently
        // aggregated value since it is complete.
        // -> ts age in [0, T)
        //
        // If it is larger (e.g. hourly, daily), we need to grab the previous period since
        // the most recent value will only be partially aggregated.
        // -> ts age in [T, 2T)
        //
        // This is because each datapoint for a period > PT1M represents all minutes
        // after that timestamp up to the next period.
        //
        // Note: In the special case of end alignment, the timestamps returned will
        // ALWAYS be a subset of {now - T,  now}, which means the age of each
        // point is always either 0 or T. Since these are equal to the lower bound
        // depending on the period, it's still handled correctly.
        final Duration minAge = period.equals(ONE_MINUTE) ? Duration.ZERO : period;
        final Duration maxAge = period.equals(ONE_MINUTE) ? period : period.multipliedBy(2);

        return IntStream.range(0, values.size())
                .mapToObj(i -> values.get(values.size() - 1 - i).getTime()) // grab times in reverse
                .map(t -> Duration.between(t, adjustedScheduled))
                .anyMatch(inRangeExcludingEnd(minAge, maxAge));
    }

    private <T extends Comparable<T>> Predicate<T> inRangeExcludingEnd(final T minInclusive, final T maxExclusive) {
        return x -> x.compareTo(minInclusive) >= 0 && x.compareTo(maxExclusive) < 0;
    }

    private Optional<TimeSeriesResult.QueryTagGroupBy> getTagGroupBy(final TimeSeriesResult.Result result) {
        // We don't expect the tag group-by to be the only value in the stream
        // because the histogram plugin uses its own group-by.
        return result.getGroupBy().stream()
                .filter(g -> g instanceof TimeSeriesResult.QueryTagGroupBy)
                .map(g -> (TimeSeriesResult.QueryTagGroupBy) g)
                .findFirst();
    }

    private BoundedMetricsQuery applyTimeRange(final MetricsQuery query,
                                               final Instant scheduled,
                                               final QueryWindow window) {

        final Instant adjustedScheduled = scheduled.minus(_queryOffset);

        final QueryAlignment alignment = window.getAlignment();
        final Instant truncatedScheduled;
        if (alignment.equals(QueryAlignment.PERIOD)) {
            // period-alignment means the window should span the most recent period,
            // so we need to truncate the current time, effectively shifting the window.
            final long excessMillis = adjustedScheduled.toEpochMilli() % window.getLookbackPeriod().toMillis();
            truncatedScheduled = adjustedScheduled.minusMillis(excessMillis);
        } else if (alignment.equals(QueryAlignment.END)) {
            // end-alignment means the end of the window should be the current time.
            // no modification is required.
            truncatedScheduled = adjustedScheduled;
        } else {
            throw new IllegalArgumentException("Unsupported or unimplemented alignment type: " + alignment);
        }

        final ZonedDateTime endTime = truncatedScheduled.atZone(ZoneOffset.UTC);
        final ZonedDateTime startTime = endTime.minus(window.getLookbackPeriod());

        return new DefaultBoundedMetricsQuery.Builder()
                .setStartTime(startTime)
                .setEndTime(endTime)
                .setQuery(query.getQuery())
                .setFormat(query.getQueryFormat())
                .build();
    }

    private static CompletionException newCompletionException(
            final String problemCode,
            final String message,
            final ImmutableList<?> args
    ) {
        final Problem problem = new Problem.Builder()
                .setProblemCode(problemCode)
                .setArgs(ImmutableList.copyOf(args))
                .build();
        return new CompletionException(
                new AlertExecutionException(message, ImmutableList.of(problem))
        );
    }
}
