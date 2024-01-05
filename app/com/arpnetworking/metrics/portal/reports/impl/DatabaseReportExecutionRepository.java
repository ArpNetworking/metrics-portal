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

import com.arpnetworking.metrics.portal.reports.ReportExecutionRepository;
import com.arpnetworking.metrics.portal.scheduling.JobExecutionRepository;
import com.arpnetworking.metrics.portal.scheduling.impl.DatabaseExecutionHelper;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import io.ebean.Database;
import io.ebean.ExpressionList;
import models.ebean.ReportExecution;
import models.internal.Organization;
import models.internal.reports.Report;
import models.internal.scheduling.JobExecution;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityNotFoundException;

/**
 * Implementation of {@link JobExecutionRepository} for {@link Report} jobs using a SQL database.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class DatabaseReportExecutionRepository implements ReportExecutionRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseReportExecutionRepository.class);

    private final AtomicBoolean _isOpen = new AtomicBoolean(false);
    private final Database _ebeanServer;
    private final DatabaseExecutionHelper<Report.Result, ReportExecution> _executionHelper;
    private final Executor _executor;

    /**
     * Public constructor.
     *
     * @param ebeanServer Play's {@code Database} for this repository.
     * @param executor The executor to spawn futures onto.
     */
    @Inject
    public DatabaseReportExecutionRepository(@Named("metrics_portal") final Database ebeanServer, final Executor executor) {
        _ebeanServer = ebeanServer;
        _executionHelper = new DatabaseExecutionHelper<>(LOGGER, _ebeanServer, this::findOrCreateReportExecution, executor);
        _executor = executor;

    }

    private CompletionStage<ReportExecution> findOrCreateReportExecution(
            final UUID jobId,
            final Organization organization,
            final Instant scheduled
    ) {
        return CompletableFuture.supplyAsync(() -> {
            final Optional<models.ebean.Report> report = models.ebean.Organization.findByOrganization(_ebeanServer,
                    organization)
                    .flatMap(beanOrg -> models.ebean.Report.findByUUID(
                            _ebeanServer,
                            beanOrg,
                            jobId
                    ));
            if (!report.isPresent()) {
                final String message = String.format(
                        "Could not find report with uuid=%s, organization.uuid=%s",
                        jobId,
                        organization.getId()
                );
                throw new EntityNotFoundException(message);
            }

            final Optional<ReportExecution> existingExecution = report.flatMap(r ->
                    _ebeanServer.createQuery(ReportExecution.class)
                            .where()
                            .eq("report", r)
                            .eq("scheduled", scheduled)
                            .findOneOrEmpty()
            );
            final ReportExecution newOrUpdatedExecution = existingExecution.orElse(new ReportExecution());
            newOrUpdatedExecution.setReport(report.get());
            newOrUpdatedExecution.setScheduled(scheduled);
            return newOrUpdatedExecution;
        }, _executor);
    }

    private ExpressionList<ReportExecution> findExecutions(final UUID jobId, final Organization organization) {
        return _ebeanServer.find(ReportExecution.class)
                .where()
                .eq("report.uuid", jobId)
                .eq("report.organization.uuid", organization.getId());
    }

    @Override
    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening DatabaseReportExecutionRepository").log();
        _isOpen.set(true);
    }

    @Override
    public void close() {
        assertIsOpen();
        LOGGER.debug().setMessage("Closing DatabaseReportExecutionRepository").log();
        _isOpen.set(false);
    }

    @Override
    public CompletionStage<Optional<JobExecution<Report.Result>>> getLastScheduled(final UUID jobId, final Organization organization)
            throws NoSuchElementException {
        assertIsOpen();
        return CompletableFuture.supplyAsync(() ->
            findExecutions(jobId, organization)
                .setMaxRows(1)
                .orderBy()
                .desc("scheduled")
                .findOneOrEmpty()
                .map(DatabaseExecutionHelper::toInternalModel),
            _executor
        );
    }

    @Override
    public CompletionStage<Optional<JobExecution.Success<Report.Result>>> getLastSuccess(final UUID jobId, final Organization organization)
            throws NoSuchElementException {
        assertIsOpen();
        return CompletableFuture.supplyAsync(() -> {
            final Optional<ReportExecution> row =
                    findExecutions(jobId, organization)
                            .eq("state", ReportExecution.State.SUCCESS)
                            .orderBy()
                            .desc("completed_at")
                            .setMaxRows(1)
                            .findOneOrEmpty();
            if (row.isPresent()) {
                final JobExecution<Report.Result> execution = DatabaseExecutionHelper.toInternalModel(row.get());
                if (execution instanceof JobExecution.Success) {
                    return Optional.of((JobExecution.Success<Report.Result>) execution);
                }
                throw new IllegalStateException(
                        String.format("execution returned was not a success when specified by the query: %s", row.get())
                );
            }
            return Optional.empty();
        }, _executor);
    }

    @Override
    public CompletionStage<Optional<JobExecution<Report.Result>>> getLastCompleted(final UUID jobId, final Organization organization)
            throws NoSuchElementException {
        assertIsOpen();
        return CompletableFuture.supplyAsync(() ->
            findExecutions(jobId, organization)
                    .in("state", ReportExecution.State.SUCCESS, ReportExecution.State.FAILURE)
                    .orderBy()
                    .desc("completed_at")
                    .setMaxRows(1)
                    .findOneOrEmpty()
                    .map(DatabaseExecutionHelper::toInternalModel),
            _executor
        );
    }

    @Override
    public CompletionStage<Void> jobStarted(final UUID reportId, final Organization organization, final Instant scheduled) {
        assertIsOpen();
        return _executionHelper.jobStarted(reportId, organization, scheduled);
    }

    @Override
    public CompletionStage<JobExecution.Success<Report.Result>> jobSucceeded(
            final UUID reportId,
            final Organization organization,
            final Instant scheduled,
            final Report.Result result
    ) {
        assertIsOpen();
        return _executionHelper.jobSucceeded(reportId, organization, scheduled, result)
            .thenApply(DatabaseExecutionHelper::toInternalModel)
            .thenApply(e -> {
                if (!(e instanceof JobExecution.Success)) {
                    throw new IllegalStateException("not a success");
                }
                return (JobExecution.Success<Report.Result>) e;
            });
    }

    @Override
    public CompletionStage<Void> jobFailed(
            final UUID reportId,
            final Organization organization,
            final Instant scheduled,
            final Throwable error
    ) {
        assertIsOpen();
        return _executionHelper.jobFailed(reportId, organization, scheduled, error);
    }

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_isOpen.get() != expectedState) {
            throw new IllegalStateException(String.format("DatabaseReportExecutionRepository is not %s",
                    expectedState ? "open" : "closed"));
        }
    }
}
