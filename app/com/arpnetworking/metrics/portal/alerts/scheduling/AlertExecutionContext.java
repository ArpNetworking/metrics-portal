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

import com.arpnetworking.metrics.portal.scheduling.Schedule;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.scheduling.Job;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;

/**
 * Utility class for scheduling and evaluating alerts.
 *
 * This essentially encapsulates the dependencies and functionality necessary to
 * allow {@link Alert} instances to implement the {@link Job} interface.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class AlertExecutionContext {
    private final Schedule _defaultSchedule;

    /**
     * Default constructor.
     *
     * @param defaultSchedule The default alert execution schedule.
     */
    @Inject
    public AlertExecutionContext(
            final Schedule defaultSchedule
    ) {
        _defaultSchedule = defaultSchedule;
    }

    /**
     * Evaluate an alert that was scheduled for the given instant.
     *
     * @param alert The alert to evaluate.
     * @param scheduled The scheduled evaluation time.
     * @return A completion stage containing {@code AlertEvaluationResult}.
     */
    public CompletionStage<AlertEvaluationResult> execute(final Alert alert, final Instant scheduled) {
        final CompletableFuture<AlertEvaluationResult> future = new CompletableFuture<>();
        future.completeExceptionally(new UnsupportedOperationException("Alert execution is not implemented"));
        return future;
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
