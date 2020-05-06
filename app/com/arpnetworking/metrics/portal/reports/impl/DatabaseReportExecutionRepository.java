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
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import io.ebean.EbeanServer;
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
    private final DatabaseExecutionRepositoryHelper<Report.Result, ReportExecution> _helper;

    /**
     * Public constructor.
     *
     * @param ebeanServer Play's {@code EbeanServer} for this repository.
     */
    @Inject
    public DatabaseReportExecutionRepository(@Named("metrics_portal") final EbeanServer ebeanServer) {
        _ebeanServer = ebeanServer;
        _helper = new DatabaseExecutionRepositoryHelper<>(LOGGER, _ebeanServer, this::reportExecutionSupplier);

    }

    private ReportExecution reportExecutionSupplier(
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
    public Optional<JobExecution<Report.Result>> getLastScheduled(final UUID jobId, final Organization organization)
            throws NoSuchElementException {
        assertIsOpen();
        return _ebeanServer.find(ReportExecution.class)
                .orderBy()
                .desc("scheduled")
                .where()
                .eq("report.uuid", jobId)
                .eq("report.organization.uuid", organization.getId())
                .setMaxRows(1)
                .findOneOrEmpty()
                .map(DatabaseExecutionRepositoryHelper::toInternalModel);
    }

    @Override
    public Optional<JobExecution.Success<Report.Result>> getLastSuccess(final UUID jobId, final Organization organization)
            throws NoSuchElementException {
        assertIsOpen();
        final Optional<ReportExecution> row = _ebeanServer.find(ReportExecution.class)
                .orderBy()
                .desc("completed_at")
                .where()
                .eq("report.uuid", jobId)
                .eq("report.organization.uuid", organization.getId())
                .eq("state", ReportExecution.State.SUCCESS)
                .setMaxRows(1)
                .findOneOrEmpty();
        if (row.isPresent()) {
            final JobExecution<Report.Result> execution = DatabaseExecutionRepositoryHelper.toInternalModel(row.get());
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
        return _ebeanServer.find(ReportExecution.class)
                .orderBy()
                .desc("completed_at")
                .where()
                .eq("report.uuid", jobId)
                .eq("report.organization.uuid", organization.getId())
                .in("state", ReportExecution.State.SUCCESS, ReportExecution.State.FAILURE)
                .setMaxRows(1)
                .findOneOrEmpty()
                .map(DatabaseExecutionRepositoryHelper::toInternalModel);
    }

    @Override
    public void jobStarted(final UUID reportId, final Organization organization, final Instant scheduled) {
        assertIsOpen();
        _helper.jobStarted(reportId, organization, scheduled);
    }

    @Override
    public void jobSucceeded(final UUID reportId, final Organization organization, final Instant scheduled, final Report.Result result) {
        assertIsOpen();
        _helper.jobSucceeded(reportId, organization, scheduled, result);
    }

    @Override
    public void jobFailed(final UUID reportId, final Organization organization, final Instant scheduled, final Throwable error) {
        assertIsOpen();
        _helper.jobFailed(reportId, organization, scheduled, error);
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
