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
import io.ebean.EbeanServer;
import io.ebean.ExpressionList;
import models.ebean.ReportExecution;
import models.internal.Organization;
import models.internal.reports.Report;
import models.internal.scheduling.JobExecution;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
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
    private final EbeanServer _ebeanServer;
    private final DatabaseExecutionHelper<Report.Result, ReportExecution> _executionHelper;

    /**
     * Public constructor.
     *
     * @param ebeanServer Play's {@code EbeanServer} for this repository.
     */
    @Inject
    public DatabaseReportExecutionRepository(@Named("metrics_portal") final EbeanServer ebeanServer) {
        _ebeanServer = ebeanServer;
        _executionHelper = new DatabaseExecutionHelper<>(LOGGER, _ebeanServer, this::findOrCreateReportExecution);

    }

    private ReportExecution findOrCreateReportExecution(
            final UUID jobId,
            final Organization organization,
            final Instant scheduled
    ) {
        final Optional<models.ebean.Report> report = models.ebean.Organization.findByOrganization(_ebeanServer, organization)
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
    public Optional<JobExecution<Report.Result>> getLastScheduled(final UUID jobId, final Organization organization)
            throws NoSuchElementException {
        assertIsOpen();
        return findExecutions(jobId, organization)
                .setMaxRows(1)
                .orderBy()
                .desc("scheduled")
                .findOneOrEmpty()
                .map(DatabaseExecutionHelper::toInternalModel);
    }

    @Override
    public Optional<JobExecution.Success<Report.Result>> getLastSuccess(final UUID jobId, final Organization organization)
            throws NoSuchElementException {
        assertIsOpen();
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
    }

    @Override
    public Optional<JobExecution<Report.Result>> getLastCompleted(final UUID jobId, final Organization organization)
            throws NoSuchElementException {
        assertIsOpen();
        return findExecutions(jobId, organization)
                .in("state", ReportExecution.State.SUCCESS, ReportExecution.State.FAILURE)
                .orderBy()
                .desc("completed_at")
                .setMaxRows(1)
                .findOneOrEmpty()
                .map(DatabaseExecutionHelper::toInternalModel);
    }

    @Override
    public void jobStarted(final UUID reportId, final Organization organization, final Instant scheduled) {
        assertIsOpen();
        _executionHelper.jobStarted(reportId, organization, scheduled);
    }

    @Override
    public void jobSucceeded(final UUID reportId, final Organization organization, final Instant scheduled, final Report.Result result) {
        assertIsOpen();
        _executionHelper.jobSucceeded(reportId, organization, scheduled, result);
    }

    @Override
    public void jobFailed(final UUID reportId, final Organization organization, final Instant scheduled, final Throwable error) {
        assertIsOpen();
        _executionHelper.jobFailed(reportId, organization, scheduled, error);
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
