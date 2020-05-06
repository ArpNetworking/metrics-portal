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

package com.arpnetworking.metrics.portal.reports.impl;

import com.arpnetworking.metrics.portal.scheduling.JobExecutionRepository;
import com.arpnetworking.steno.Logger;
import com.google.common.base.Throwables;
import io.ebean.EbeanServer;
import io.ebean.Transaction;
import models.ebean.BaseExecution;
import models.internal.Organization;
import models.internal.scheduling.JobExecution;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.persistence.PersistenceException;

/**
 * Helper class for implementing a SQL-backed {@link JobExecutionRepository}.
 * <p>
 * Classes using this should extend {@link BaseExecution} and delegate their {@code jobXXX} calls to this helper.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class DatabaseExecutionRepositoryHelper<T, E extends BaseExecution<T>> {
    private final EbeanServer _ebeanServer;
    private final ExecutionSupplier<T, E> _supplier;
    private final Logger _logger;

    DatabaseExecutionRepositoryHelper(
            final Logger logger,
            final EbeanServer ebeanServer,
            final ExecutionSupplier<T, E> supplier
    ) {
        _ebeanServer = ebeanServer;
        _supplier = supplier;
        _logger = logger;
    }

    /**
     * Convert a bean to its internal representation.
     *
     * @param beanModel The bean model.
     * @param <T> The type of the result possibly contained in the execution.
     * @param <E> The type of the bean execution result.
     * @return An internal model for this execution
     */
    public static <T, E extends BaseExecution<T>> JobExecution<T> toInternalModel(final E beanModel) {
        final BaseExecution.State state = beanModel.getState();
        switch (state) {
            case STARTED:
                return new JobExecution.Started.Builder<T>()
                        .setJobId(beanModel.getJobId())
                        .setScheduled(beanModel.getScheduled())
                        .setStartedAt(beanModel.getStartedAt())
                        .build();
            case FAILURE:
                @Nullable final Throwable throwable = beanModel.getError() == null ? null : new Throwable(beanModel.getError());
                return new JobExecution.Failure.Builder<T>()
                        .setJobId(beanModel.getJobId())
                        .setScheduled(beanModel.getScheduled())
                        .setStartedAt(beanModel.getStartedAt())
                        .setCompletedAt(beanModel.getCompletedAt())
                        .setError(throwable)
                        .build();
            case SUCCESS:
                return new JobExecution.Success.Builder<T>()
                        .setJobId(beanModel.getJobId())
                        .setScheduled(beanModel.getScheduled())
                        .setCompletedAt(beanModel.getCompletedAt())
                        .setStartedAt(beanModel.getStartedAt())
                        .setResult(beanModel.getResult())
                        .build();
            default:
                throw new AssertionError("unexpected state: " + state);
        }
    }

    /**
     * Mark this job as started.
     *
     * @param jobId
     * @param organization
     * @param scheduled
     */
    public void jobStarted(final UUID jobId, final Organization organization, final Instant scheduled) {
        updateExecutionState(
                jobId,
                organization,
                scheduled,
                BaseExecution.State.STARTED,
                execution -> {
                    execution.setStartedAt(Instant.now());
                }
        );
    }

    /**
     * Mark this job as having succeeded with the given result.
     *
     * @param jobId
     * @param organization
     * @param scheduled
     */
    public void jobSucceeded(final UUID jobId, final Organization organization, final Instant scheduled, final T result) {
        updateExecutionState(
                jobId,
                organization,
                scheduled,
                BaseExecution.State.SUCCESS,
                execution -> {
                    execution.setResult(result);
                    execution.setCompletedAt(Instant.now());
                }
        );
    }

    /**
     * Mark this job as having failed with the given error.
     *
     * @param jobId
     * @param organization
     * @param scheduled
     * @param error
     */
    public void jobFailed(final UUID jobId, final Organization organization, final Instant scheduled, final Throwable error) {
        updateExecutionState(
                jobId,
                organization,
                scheduled,
                BaseExecution.State.FAILURE,
                execution -> {
                    execution.setError(Throwables.getStackTraceAsString(error));
                    execution.setCompletedAt(Instant.now());
                }
        );
    }

    private void updateExecutionState(
            final UUID jobId,
            final Organization organization,
            final Instant scheduled,
            final BaseExecution.State state,
            final Consumer<E> update
    ) {
        _logger.debug()
                .setMessage("Upserting job execution")
                .addData("job.uuid", jobId)
                .addData("scheduled", scheduled)
                .addData("state", state)
                .log();
        try (Transaction transaction = _ebeanServer.beginTransaction()) {
            final E newOrUpdatedExecution = _supplier.findOrCreateExecution(jobId, organization, scheduled);
            update.accept(newOrUpdatedExecution);
            newOrUpdatedExecution.setState(state);
            _ebeanServer.save(newOrUpdatedExecution);

            _logger.debug()
                    .setMessage("Upserted job execution")
                    .addData("job.uuid", jobId)
                    .addData("scheduled", scheduled)
                    .addData("state", state)
                    .log();
            transaction.commit();
            // CHECKSTYLE.OFF: IllegalCatchCheck
        } catch (final RuntimeException e) {
            // CHECKSTYLE.ON: IllegalCatchCheck
            _logger.error()
                    .setMessage("Failed to job report executions")
                    .addData("job.uuid", jobId)
                    .addData("scheduled", scheduled)
                    .addData("state", state)
                    .setThrowable(e)
                    .log();
            throw new PersistenceException("Failed to upsert job executions", e);
        }
    }

    @FunctionalInterface
    interface ExecutionSupplier<T, E extends BaseExecution<T>> {
        E findOrCreateExecution(UUID jobId, Organization organization, Instant scheduled);
    }
}
