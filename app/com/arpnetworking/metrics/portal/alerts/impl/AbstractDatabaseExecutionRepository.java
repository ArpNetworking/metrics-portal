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

import com.arpnetworking.metrics.portal.scheduling.JobExecutionRepository;
import com.arpnetworking.steno.Logger;
import com.google.common.base.Throwables;
import io.ebean.EbeanServer;
import io.ebean.ExpressionList;
import io.ebean.Transaction;
import models.ebean.BaseExecution;
import models.internal.Organization;
import models.internal.scheduling.JobExecution;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.persistence.PersistenceException;

/**
 * Base implementation of {@link JobExecutionRepository} using a SQL database.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public abstract class AbstractDatabaseExecutionRepository<T, E extends BaseExecution<T>> implements JobExecutionRepository<T> {

    private final Logger _logger;
    private final AtomicBoolean _isOpen = new AtomicBoolean(false);
    private final EbeanServer _ebeanServer;
    private final ExecutionAdapter<T, E> _adapter;

    /**
     * Public constructor.
     *
     * @param ebeanServer Play's {@code EbeanServer} for this repository.
     * @param logger the logger to use for this database.
     * @param adapter the adapter
     */
    @Inject
    public AbstractDatabaseExecutionRepository(
            final EbeanServer ebeanServer,
            final Logger logger,
            final ExecutionAdapter<T, E> adapter
    ) {
        _ebeanServer = ebeanServer;
        _logger = logger;
        _adapter = adapter;
    }

    @Override
    public void open() {
        assertIsOpen(false);
        _logger.debug().setMessage(String.format("Opening %s", getClass().getSimpleName())).log();
        _isOpen.set(true);
    }

    @Override
    public void close() {
        assertIsOpen();
        _logger.debug().setMessage(String.format("Closing %s", getClass().getSimpleName())).log();
        _isOpen.set(false);
    }

    /**
     * Get the most recently scheduled execution, if any.
     * <p>
     * This could possibly return an execution that's pending completion.
     *
     * @param jobId The UUID of the job that completed.
     * @param organization The organization owning the job.
     * @return The last successful execution.
     * @throws NoSuchElementException if no job has the given UUID.
     */
    public Optional<JobExecution<T>> getLastScheduled(final UUID jobId, final Organization organization)
            throws NoSuchElementException {
        assertIsOpen();
        return _adapter.findOneQuery(jobId, organization)
                .orderBy()
                .desc("scheduled")
                .setMaxRows(1)
                .findOneOrEmpty()
                .map(this::toInternalModel);
    }

    @Override
    public Optional<JobExecution.Success<T>> getLastSuccess(final UUID jobId, final Organization organization)
            throws NoSuchElementException {
        assertIsOpen();
        final Optional<E> row =
                _adapter.findOneQuery(jobId, organization)
                        .in("state", BaseExecution.State.SUCCESS)
                        .orderBy()
                        .desc("completed_at")
                        .setMaxRows(1)
                        .findOneOrEmpty();
        if (row.isPresent()) {
            final JobExecution<T> execution = toInternalModel(row.get());
            if (execution instanceof JobExecution.Success) {
                return Optional.of((JobExecution.Success<T>) execution);
            }
            throw new IllegalStateException(
                    String.format("execution returned was not a success when specified by the query: %s", row.get())
            );
        }
        return Optional.empty();
    }

    @Override
    public Optional<JobExecution<T>> getLastCompleted(final UUID jobId, final Organization organization)
            throws NoSuchElementException {
        assertIsOpen();
        return _adapter.findOneQuery(jobId, organization)
                .in("state", BaseExecution.State.SUCCESS, BaseExecution.State.FAILURE)
                .orderBy()
                .desc("completed_at")
                .setMaxRows(1)
                .findOneOrEmpty()
                .map(this::toInternalModel);
    }

    @Override
    public void jobStarted(final UUID jobId, final Organization organization, final Instant scheduled) {
        assertIsOpen();
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

    @Override
    public void jobSucceeded(final UUID jobId, final Organization organization, final Instant scheduled, final T result) {
        assertIsOpen();
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

    @Override
    public void jobFailed(final UUID jobId, final Organization organization, final Instant scheduled, final Throwable error) {
        assertIsOpen();
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

    // This could be overwritten
    private void updateExecutionState(
            final UUID jobId,
            final Organization organization,
            final Instant scheduled,
            final BaseExecution.State state,
            final Consumer<E> update
    ) {
        _logger.debug()
                .setMessage("Upserting execution")
                .addData("job.uuid", jobId)
                .addData("scheduled", scheduled)
                .addData("state", state)
                .log();
        try (Transaction transaction = _ebeanServer.beginTransaction()) {
            final Optional<E> existingExecution =
                    _adapter.findOneQuery(jobId, organization)
                            .eq("scheduled", scheduled)
                            .setMaxRows(1)
                            .findOneOrEmpty();
            final E newOrUpdatedExecution = existingExecution.orElse(_adapter.newExecution(jobId, organization));
            newOrUpdatedExecution.setJobId(jobId);
            newOrUpdatedExecution.setScheduled(scheduled);

            update.accept(newOrUpdatedExecution);
            newOrUpdatedExecution.setState(state);

            if (existingExecution.isPresent()) {
                _ebeanServer.update(newOrUpdatedExecution);
            } else {
                _ebeanServer.save(newOrUpdatedExecution);
            }

            _logger.debug()
                    .setMessage("Upserted execution")
                    .addData("job.uuid", jobId)
                    .addData("scheduled", scheduled)
                    .addData("state", state)
                    .log();
            transaction.commit();
            // CHECKSTYLE.OFF: IllegalCatchCheck
        } catch (final RuntimeException e) {
            // CHECKSTYLE.ON: IllegalCatchCheck
            _logger.error()
                    .setMessage("Failed to upsert executions")
                    .addData("job.uuid", jobId)
                    .addData("scheduled", scheduled)
                    .addData("state", state)
                    .setThrowable(e)
                    .log();
            throw new PersistenceException("Failed to upsert executions", e);
        }
    }

    private JobExecution<T> toInternalModel(final E beanModel) {
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

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_isOpen.get() != expectedState) {
            throw new IllegalStateException(String.format("%s is not %s",
                    getClass().getSimpleName(),
                    expectedState ? "open" : "closed"));
        }
    }

    public interface ExecutionAdapter<T, E extends BaseExecution<T>> {
        ExpressionList<E> findOneQuery(UUID jobId, Organization organization);

        E newExecution(UUID jobId, Organization organization);
    }
}
