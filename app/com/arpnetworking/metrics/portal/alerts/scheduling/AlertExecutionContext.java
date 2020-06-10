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
import models.internal.MetricsQueryResult;
import models.internal.TimeSeriesResult;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.impl.DefaultAlertEvaluationResult;
import models.internal.scheduling.Job;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

        // Query result expectations:
        //
        // - There is only ever a single query.
        // - There are either multiple results, each containing a tag group-by,
        //   or a single result, with *no* tag group-by.
        // - The name field of each result should be identical, since the only
        //   difference should be in the group-by and values.

        final ImmutableList<? extends TimeSeriesResult.Query> queries = queryResult.getQueryResult().getQueries();
        if (queries.size() != 1) {
            throw new IllegalStateException("Alert definitions should only ever contain a single query.");
        }
        final TimeSeriesResult.Query query = queries.get(0);
        if (query.getResults().isEmpty()) {
            throw new IllegalStateException("Expected at least one result.");
        }

        final long uniqueNames = query.getResults().stream()
                .map(TimeSeriesResult.Result::getName)
                .distinct()
                .count();

        final String name;
        if (uniqueNames > 1) {
            throw new IllegalStateException("Received more than one metric name back in the results");
        }
        name = query.getResults().get(0).getName();

        final List<Map<String, String>> firingTags =
                query.getResults()
                        .stream()
                        .map(this::getTagGroupBys)
                        .map(TimeSeriesResult.QueryTagGroupBy::getGroup)
                        .collect(ImmutableList.toImmutableList());

        // FIXME: Wtf do we do if an alert is firing and there are *no* group-bys.
        //
        // I think this might be better expressed as:
        //  setFiringGroups :: List<FiringGroup>
        //
        //  FiringGroup :: {
        //      name = String,
        //      tags = Map<String, String>,
        //  }
        //

        return new DefaultAlertEvaluationResult.Builder()
                .setName(name)
                .setFiringTags(firingTags)
                .build();
    }

    private TimeSeriesResult.QueryTagGroupBy getTagGroupBys(final TimeSeriesResult.Result result) {
        // If there is not a supported group by in here we should throw because
        // that means that we got a result back we weren't expecting.
        final ImmutableList<TimeSeriesResult.QueryTagGroupBy> groupBys = result.getGroupBy().stream()
                .filter(g -> g instanceof TimeSeriesResult.QueryTagGroupBy)
                .map(g -> (TimeSeriesResult.QueryTagGroupBy) g)
                .collect(ImmutableList.toImmutableList());
        if (groupBys.size() != 1) {
            throw new IllegalStateException("Result group-bys does not contain any known types");
        }
        return groupBys.get(0);
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
