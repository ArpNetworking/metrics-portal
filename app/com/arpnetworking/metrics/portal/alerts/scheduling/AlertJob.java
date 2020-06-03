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
import com.google.inject.Injector;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.scheduling.Job;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * A wrapper around {@code Alert} instances to allow for scheduling and evaluation of Alert queries.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
/* package private */ final class AlertJob implements Job<AlertEvaluationResult> {
    private final Alert _alert;
    private final AlertExecutionContext _context;

    /**
     * Create a job from an alert.
     *
     * @param alert The alert that this job will evaluate.
     * @param context The execution context.
     */
    /* package private */ AlertJob(final Alert alert, final AlertExecutionContext context) {
        _alert = alert;
        _context = context;
    }

    @Override
    public UUID getId() {
        return _alert.getId();
    }

    @Override
    public Optional<String> getETag() {
        return Optional.empty();
    }

    @Override
    public Schedule getSchedule() {
        return _context.getSchedule(_alert);
    }

    @Override
    public Duration getTimeout() {
        return Duration.of(1, ChronoUnit.MINUTES);
    }

    @Override
    public CompletionStage<? extends AlertEvaluationResult> execute(final Injector injector, final Instant scheduled) {
        return _context.execute(_alert, scheduled);
    }
}
