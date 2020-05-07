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
package com.arpnetworking.metrics.portal.alerts.impl;

import com.arpnetworking.metrics.portal.alerts.AlertExecutionRepository;
import models.internal.Organization;
import models.internal.alerts.FiringAlertResult;
import models.internal.scheduling.JobExecution;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * An empty {@code AlertExecutionRepository}.
 *
 * @author Christian Briones (cbriones at dropbox dot com).
 */
public final class NoAlertExecutionRepository implements AlertExecutionRepository {
    @Override
    public void open() {

    }

    @Override
    public void close() {

    }

    @Override
    public Optional<JobExecution.Success<FiringAlertResult>> getLastSuccess(
            final UUID jobId, final Organization organization
    ) throws NoSuchElementException {
        return Optional.empty();
    }

    @Override
    public Optional<JobExecution<FiringAlertResult>> getLastCompleted(
            final UUID jobId,
            final Organization organization
    ) throws NoSuchElementException {
        return Optional.empty();
    }

    @Override
    public void jobStarted(final UUID jobId, final Organization organization, final Instant scheduled) {

    }

    @Override
    public void jobSucceeded(final UUID jobId, final Organization organization, final Instant scheduled, final FiringAlertResult result) {

    }

    @Override
    public void jobFailed(final UUID jobId, final Organization organization, final Instant scheduled, final Throwable error) {

    }
}
