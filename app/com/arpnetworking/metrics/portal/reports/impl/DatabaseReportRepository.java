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
import models.ebean.Report;
import models.ebean.ReportExecution;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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
    public Optional<Report> getReport(final UUID identifier) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Getting report")
                .addData("uuid", identifier)
                .log();
        return Ebean.find(Report.class)
                .where()
                .eq("uuid", identifier)
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

    @Override
    public void jobCompleted(final Report report, final Report.State state, final ZonedDateTime completionTime) {
        assertIsOpen();

        final ReportExecution execution = new ReportExecution();
        execution.setReport(report);
        execution.setExecutedAt(completionTime);
        execution.setState(state);

        LOGGER.debug()
                .setMessage("Updating report executions")
                .addData("report", report)
                .addData("completionTime", completionTime)
                .addData("state", state)
                .log();
        try {
            Ebean.save(execution);
            LOGGER.debug()
                    .setMessage("Updated report execution")
                    .addData("report", report)
                    .addData("completionTime", completionTime)
                    .addData("state", state)
                    .log();
            // CHECKSTYLE.OFF: IllegalCatchCheck
        } catch (final RuntimeException e) {
            // CHECKSTYLE.ON: IllegalCatchCheck
            LOGGER.error()
                    .setMessage("Failed to update report executions")
                    .addData("report", report)
                    .addData("completionTime", completionTime)
                    .addData("state", state)
                    .setThrowable(e)
                    .log();
            throw new PersistenceException(e);
        }
    }

    Optional<ReportExecution> getMostRecentExecution(final Report report) {
        return Ebean.find(ReportExecution.class)
                .orderBy()
                .desc("executed_at")
                .where()
                .eq("report_id", report.getId())
                .findOneOrEmpty();
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
// CHECKSTYLE.ON: IllegalCatchCheck
