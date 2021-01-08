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
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import models.internal.Organization;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.scheduling.JobExecution;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An empty {@code AlertExecutionRepository}.
 *
 * @author Christian Briones (cbriones at dropbox dot com).
 */
public final class NoAlertExecutionRepository implements AlertExecutionRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(NoAlertExecutionRepository.class);
    private final AtomicBoolean _isOpen = new AtomicBoolean(false);

    @Override
    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening NoAlertExecutionRepository").log();
        _isOpen.set(true);
    }

    @Override
    public void close() {
        assertIsOpen(true);
        LOGGER.debug().setMessage("Closing NoAlertExecutionRepository").log();
        _isOpen.set(false);
    }

    @Override
    public CompletionStage<Optional<JobExecution<AlertEvaluationResult>>> getLastScheduled(
            final UUID jobId,
            final Organization organization
    ) {
        assertIsOpen();
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletionStage<Optional<JobExecution.Success<AlertEvaluationResult>>> getLastSuccess(
            final UUID jobId, final Organization organization
    ) throws NoSuchElementException {
        assertIsOpen();
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletionStage<Optional<JobExecution<AlertEvaluationResult>>> getLastCompleted(
            final UUID jobId,
            final Organization organization
    ) throws NoSuchElementException {
        assertIsOpen();
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletionStage<Void> jobStarted(
            final UUID jobId,
            final Organization organization,
            final Instant scheduled
    ) {
        assertIsOpen();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<JobExecution.Success<AlertEvaluationResult>> jobSucceeded(
            final UUID jobId,
            final Organization organization,
            final Instant scheduled,
            final AlertEvaluationResult result
    ) {
        assertIsOpen();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> jobFailed(
            final UUID jobId,
            final Organization organization,
            final Instant scheduled,
            final Throwable error
    ) {
        assertIsOpen();
        return CompletableFuture.completedFuture(null);
    }


    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_isOpen.get() != expectedState) {
            throw new IllegalStateException(String.format("NoAlertExecutionRepository is not %s",
                    expectedState ? "open" : "closed"));
        }
    }
}
