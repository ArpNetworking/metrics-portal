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

import com.arpnetworking.metrics.portal.query.QueryExecutionException;
import com.arpnetworking.metrics.portal.query.QueryExecutor;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import models.internal.MetricsQueryResult;
import models.internal.TimeSeriesResult;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.impl.DefaultAlertEvaluationResult;
import models.internal.scheduling.Job;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
     * Evaluate an alert that was scheduled for the given instant.
     *
     * @param alert The alert to evaluate.
     * @param scheduled The scheduled evaluation time.
     * @return A completion stage containing {@code AlertEvaluationResult}.
     */
    public CompletionStage<AlertEvaluationResult> execute(final Alert alert, final Instant scheduled) {
        CompletableFuture<MetricsQueryResult> queryStage;
        try {
            queryStage = _executor.executeQuery(alert.getQuery()).toCompletableFuture();
        } catch (final QueryExecutionException e) {
            queryStage = new CompletableFuture<>();
            queryStage.completeExceptionally(e);
        }
        return queryStage.thenApply(this::toAlertResult);
    }

    private AlertEvaluationResult toAlertResult(final MetricsQueryResult queryResult) {

        // Query result preconditions:
        //
        // - There is only ever a single query.
        // - If there are multiple results, each *must* contain a tag group-by.
        //   Otherwise there is exactly one result, which may or may not contain
        //   a tag group-by.
        // - The name field of each result should be identical, since the only
        //   difference should be in the grouping.

        final ImmutableList<? extends TimeSeriesResult.Query> queries = queryResult.getQueryResult().getQueries();
        if (queries.size() != 1) {
            throw new IllegalStateException("Alert definitions should only ever contain a single query.");
        }
        final TimeSeriesResult.Query query = queries.get(0);
        final ImmutableList<? extends TimeSeriesResult.Result> results = query.getResults();
        if (results.isEmpty()) {
            throw new IllegalStateException("Expected at least one result.");
        }

        final long uniqueNames = results.stream()
                .map(TimeSeriesResult.Result::getName)
                .distinct()
                .count();

        final String name;
        if (uniqueNames > 1) {
            throw new IllegalStateException("Received more than one metric name back in the results");
        }
        name = results.get(0).getName();

        final List<Map<String, String>> firingSeries;
        if (results.size() == 1 && !getTagGroupBy(results.get(0)).isPresent()) {
            // There was no group by.
            if (results.get(0).getValues().isEmpty()) {
                firingSeries = ImmutableList.of(ImmutableMap.of());
            } else {
                firingSeries = ImmutableList.of();
            }
        } else {
            firingSeries =
                    results
                        .stream()
                        .filter(res -> !res.getValues().isEmpty())
                        .map(res -> getTagGroupBy(res).orElseThrow(() -> new IllegalStateException("Missing tag group by")))
                        .map(TimeSeriesResult.QueryTagGroupBy::getGroup)
                        .collect(ImmutableList.toImmutableList());
        }

        return new DefaultAlertEvaluationResult.Builder()
                .setName(name)
                .setFiringTags(firingSeries)
                .build();
    }

    private Optional<TimeSeriesResult.QueryTagGroupBy> getTagGroupBy(final TimeSeriesResult.Result result) {
        // We don't expect the tag group-by to be the only value in the stream
        // because the histogram plugin uses its own group-by.
        return result.getGroupBy().stream()
                .filter(g -> g instanceof TimeSeriesResult.QueryTagGroupBy)
                .map(g -> (TimeSeriesResult.QueryTagGroupBy) g)
                .findFirst();
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
}
