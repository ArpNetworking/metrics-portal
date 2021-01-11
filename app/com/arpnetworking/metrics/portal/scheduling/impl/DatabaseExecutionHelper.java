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

package com.arpnetworking.metrics.portal.scheduling.impl;

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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.persistence.PersistenceException;

/**
 * Helper class for implementing a SQL-backed {@link JobExecutionRepository}, providing facilities for updating the state
 * of an execution as well as mapping it back to an internal representation.
 * <p>
 * Classes using this should extend {@link BaseExecution} and delegate their {@code jobXXX} calls to this helper.
 *
 * @param <T> The type of result produced by each job.
 * @param <E> The type of the repository's bean.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class DatabaseExecutionHelper<T, E extends BaseExecution<T>> {
    private final EbeanServer _ebeanServer;
    private final ExecutionAdapter<T, E> _adapter;
    private final Logger _logger;
    private Executor _executor;

    /**
     * Public constructor.
     *
     * @param logger The logger for the repository.
     * @param ebeanServer An ebean server.
     * @param adapter The execution adapter for the repository.
     * @param executor The executor to spawn futures onto.
     */
    public DatabaseExecutionHelper(
            final Logger logger,
            final EbeanServer ebeanServer,
            final ExecutionAdapter<T, E> adapter,
            final Executor executor
    ) {
        _ebeanServer = ebeanServer;
        _adapter = adapter;
        _logger = logger;
        _executor = executor;
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
        // TODO(cbriones) - This should not be static, repositories should call this from an instance.

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
     * Notify the repository that a job has started executing.
     *
     * @param jobId The UUID of the job that completed.
     * @param organization The organization owning the job.
     * @param scheduled The time that the job started running for.
     *
     * @return a future that completes when the update does
     */
    public CompletionStage<Void> jobStarted(final UUID jobId, final Organization organization, final Instant scheduled) {
        return updateExecutionState(
                jobId,
                organization,
                scheduled,
                BaseExecution.State.STARTED,
                execution -> {
                    execution.setStartedAt(Instant.now());
                }
        ).thenApply(e -> null);
    }

    /**
     * Notify the repository that a job finished executing successfully.
     *
     * @param jobId The UUID of the job that completed.
     * @param organization The organization owning the job.
     * @param scheduled The time that the completed job-run was scheduled for.
     * @param result The result that the job computed.
     *
     * @return a future that completes when the update does
     */
    public CompletionStage<E> jobSucceeded(
            final UUID jobId,
            final Organization organization,
            final Instant scheduled,
            final T result
    ) {
        return updateExecutionState(
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
     * Notify the repository that a job encountered an error and aborted execution.
     *
     * @param jobId The UUID of the job that failed.
     * @param organization The organization owning the job.
     * @param scheduled The time that the failed job-run was scheduled for.
     * @param error The exception that caused the job to fail.
     *
     * @return a future that completes when the update does
     */
    public CompletionStage<Void> jobFailed(
            final UUID jobId,
            final Organization organization,
            final Instant scheduled,
            final Throwable error
    ) {
        return updateExecutionState(
                jobId,
                organization,
                scheduled,
                BaseExecution.State.FAILURE,
                execution -> {
                    execution.setError(Throwables.getStackTraceAsString(error));
                    execution.setCompletedAt(Instant.now());
                }
        ).thenApply(exec -> null);
    }

    private CompletionStage<E> updateExecutionState(
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
        return _adapter.findOrCreateExecution(jobId, organization, scheduled)
                .whenCompleteAsync((e, error) -> {
                    if (error != null) {
                        return;
                    }
                    try (Transaction tx = _ebeanServer.beginTransaction()) {
                        update.accept(e);
                        e.setState(state);
                        _ebeanServer.save(e);
                        tx.commit();
                    }
                    _logger.debug()
                            .setMessage("Upserted job execution")
                            .addData("job.uuid", jobId)
                            .addData("scheduled", scheduled)
                            .addData("state", state)
                            .log();
                }, _executor).whenComplete((ignored, error) -> {
                    if (error == null) {
                        return;
                    }
                    _logger.error()
                            .setMessage("Failed to job report executions")
                            .addData("job.uuid", jobId)
                            .addData("scheduled", scheduled)
                            .addData("state", state)
                            .setThrowable(error)
                            .log();
                    throw new PersistenceException("Failed to upsert job executions", error);
                });
    }

    /**
     * Repository Adapter for the concrete execution type.
     *
     * @param <T> The type of result produced by each job.
     * @param <E> The type of the repository's bean.
     */
    @FunctionalInterface
    public interface ExecutionAdapter<T, E extends BaseExecution<T>> {
        /**
         * Find an execution for the scheduled time, creating a new one if none exist.
         *
         * @param jobId The id of the job associated with this execution.
         * @param organization The organization containing the job.
         * @param scheduled The time the execution was scheduled.
         * @return An execution.
         */
        CompletionStage<E> findOrCreateExecution(UUID jobId, Organization organization, Instant scheduled);
    }
}
