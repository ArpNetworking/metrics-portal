/*
 * Copyright 2018 Dropbox, Inc.
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

import com.arpnetworking.metrics.portal.reports.ReportRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import io.ebean.Ebean;
import io.ebean.Transaction;
import models.ebean.Organization;
import models.ebean.Report;
import models.ebean.ReportExecution;
import models.ebean.ReportSchedule;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceException;

/**
 * Implementation of {@link ReportRepository} using a SQL database.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class DatabaseReportRepository implements ReportRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseReportRepository.class);

    private AtomicBoolean _isOpen = new AtomicBoolean(false);

    @Override
    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening DatabaseReportRepository").log();
        _isOpen.set(true);
    }

    @Override
    public void close() {
        assertIsOpen();
        LOGGER.debug().setMessage("Closing DatabaseReportRepository").log();
        _isOpen.set(false);
    }

    @Override
    public Optional<Report> getReport(final UUID identifier, Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Getting report")
                .addData("uuid", identifier)
                .addData("organization.uuid", organization.getUuid())
                .log();
        return Ebean.find(Report.class)
                .where()
                .eq("uuid", identifier)
                .eq("organization_id", organization.getId())
                .eq("disabled", false)
                .findOneOrEmpty();
    }

    @Override
    public void addOrUpdateReport(final Report report) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Upserting report")
                .addData("report", report)
                .log();
        try (Transaction transaction = Ebean.beginTransaction()) {
            Ebean.save(report.getReportSource());
            final Optional<Report> existingReport =
                    Ebean.find(Report.class)
                            .where()
                            .eq("uuid", report.getUuid())
                            .findOneOrEmpty();
            final boolean created = !existingReport.isPresent();
            Ebean.save(report);
            transaction.commit();
            LOGGER.debug()
                    .setMessage("Upserted report")
                    .addData("report", report)
                    .addData("created", created)
                    .log();
            // CHECKSTYLE.OFF: IllegalCatchCheck
        } catch (final RuntimeException e) {
            // CHECKSTYLE.ON: IllegalCatchCheck
            LOGGER.error()
                    .setMessage("Failed to upsert report")
                    .addData("report", report)
                    .setThrowable(e)
                    .log();
            throw new PersistenceException(e);
        }
    }

    public void jobFailed(final UUID reportId, final Organization organization, final Instant scheduled) {
        assertIsOpen();
        updateExecutionState(reportId, organization, scheduled, ReportExecution.State.FAILURE);
    }

    public void jobStarted(final UUID reportId, final Organization organization, final Instant scheduled) {
        assertIsOpen();
        updateExecutionState(reportId, organization, scheduled, ReportExecution.State.STARTED);
    }

    @Override
    public void jobCompleted(final UUID reportId, final Organization organization, final Instant scheduled) {
        assertIsOpen();
        updateExecutionState(reportId, organization, scheduled, ReportExecution.State.SUCCESS);
    }

    private void updateExecutionState(final UUID reportId, final Organization organization, final Instant scheduled, final ReportExecution.State state) {
        LOGGER.debug()
                .setMessage("Updating report executions")
                .addData("report.uuid", reportId)
                .addData("scheduled", scheduled)
                .addData("state", state)
                .log();
        try (Transaction transaction = Ebean.beginTransaction()) {
            final Optional<Report> report = Ebean.find(Report.class)
                    .where()
                    .eq("uuid", reportId)
                    .eq("organization_id", organization.getId())
                    .findOneOrEmpty();
            if (!report.isPresent()) {
                throw new EntityNotFoundException();
            }

            final ReportExecution execution = new ReportExecution();
            execution.setReport(report.get());
            execution.setScheduledFor(scheduled);
            execution.setState(state);

            switch (state) {
                case SUCCESS:
                    execution.setCompletedAt(Instant.now());
                    break;
                case FAILURE:
                    execution.setCompletedAt(Instant.now());
                    break;
                case STARTED:
                    execution.setStartedAt(Instant.now());
            }

            Ebean.save(execution);
            LOGGER.debug()
                    .setMessage("Updated report execution")
                    .addData("report.uuid", reportId)
                    .addData("scheduled", scheduled)
                    .addData("state", state)
                    .log();
            transaction.commit();
            // CHECKSTYLE.OFF: IllegalCatchCheck
        } catch (final RuntimeException e) {
            // CHECKSTYLE.ON: IllegalCatchCheck
            LOGGER.error()
                    .setMessage("Failed to update report executions")
                    .addData("report.uuid", reportId)
                    .addData("scheduled", scheduled)
                    .addData("state", state)
                    .setThrowable(e)
                    .log();
            throw new PersistenceException(e);
        }
    }

    Optional<ReportExecution> getExecution(final UUID reportId, final Organization organization, final Instant scheduled) {
        return getReport(reportId, organization).flatMap(r ->
                Ebean.find(ReportExecution.class)
                        .where()
                        .eq("report_id", r.getId())
                        .eq("scheduled_for", scheduled)
                        .findOneOrEmpty()
        );
    }

    Optional<ReportExecution> getMostRecentExecution(final UUID reportId, final Organization organization) {
        return getReport(reportId, organization).flatMap(r ->
                Ebean.find(ReportExecution.class)
                        .orderBy()
                        .desc("completed_at")
                        .where()
                        .eq("report_id", r.getId())
                        .eq("state", "SUCCESS")
                        .findOneOrEmpty()
        );
    }

    private Report internalModelToBean(final com.arpnetworking.metrics.portal.reports.Report internalReport) {
        final ReportSchedule schedule = internalModelToBean(internalReport.getSchedule());

        final Report report = new Report();
        report.setUuid(internalReport.getId());
        report.setSchedule(schedule);
        return report;
    }

    private ReportSchedule internalModelToBean(final com.arpnetworking.metrics.portal.scheduling.Schedule internalSchedule) {
        return null;
    }

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_isOpen.get() != expectedState) {
            throw new IllegalStateException(String.format("DatabaseReportRepository is not %s", expectedState ? "open" : "closed"));
        }
    }
}
