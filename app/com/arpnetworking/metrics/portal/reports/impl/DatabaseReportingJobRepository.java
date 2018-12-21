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

import com.arpnetworking.metrics.portal.reports.ReportingJobRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import io.ebean.Ebean;
import io.ebean.Transaction;
import models.ebean.ReportingJob;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.persistence.PersistenceException;

/**
 * Implementation of ReportRepository using a SQL database.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class DatabaseReportingJobRepository implements ReportingJobRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseReportingJobRepository.class);

    private AtomicBoolean _isOpen = new AtomicBoolean(false);

    @Override
    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening DatabaseReportingJobRepository").log();
        _isOpen.set(true);
    }

    @Override
    public void close() {
        assertIsOpen();
        LOGGER.debug().setMessage("Closing DatabaseReportingJobRepository").log();
        _isOpen.set(false);
    }

    @Override
    public Optional<ReportingJob> getJob(final UUID identifier) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Getting reporting job")
                .addData("uuid", identifier)
                .log();
        return Ebean.find(ReportingJob.class)
                    .where()
                    .eq("uuid", identifier)
                    .findOneOrEmpty();
    }

    @Override
    public void deleteJob(final UUID uuid) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Deleting reporting job")
                .addData("uuid", uuid)
                .log();
        try {
            Ebean.find(ReportingJob.class)
                .where()
                .eq("uuid", uuid)
                .delete();
            // CHECKSTYLE.OFF: IllegalCatchCheck
        } catch (final RuntimeException e) {
            // CHECKSTYLE.ON: IllegalCatchCheck
            LOGGER.error()
                    .setMessage("Failed to delete reporting job")
                    .addData("uuid", uuid)
                    .setThrowable(e)
                    .log();
            throw new PersistenceException(e);
        }
    }

    @Override
    public void addOrUpdateJob(final ReportingJob job) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Upserting reporting job")
                .addData("job", job)
                .log();
        try (Transaction transaction = Ebean.beginTransaction()) {
//            Ebean.save(job.getRecipientGroup());
            Ebean.save(job.getReportSource());

            final Optional<ReportingJob> existingJob = getJob(job.getUuid());
            final boolean created = !existingJob.isPresent();
            Ebean.save(job);
            transaction.commit();
            LOGGER.debug()
                    .setMessage("Upserted reporting job")
                    .addData("job", job)
                    .addData("created", created)
                    .log();
            // CHECKSTYLE.OFF: IllegalCatchCheck
        } catch (final RuntimeException e) {
            // CHECKSTYLE.ON: IllegalCatchCheck
            LOGGER.error()
                    .setMessage("Failed to upsert reporting job")
                    .addData("job", job)
                    .setThrowable(e)
                    .log();
            throw new PersistenceException(e);
        }
    }

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_isOpen.get() != expectedState) {
            throw new IllegalStateException(String.format("DatabaseReportingJobRepository is not %s", expectedState ? "open" : "closed"));
        }
    }

}
// CHECKSTYLE.ON: IllegalCatchCheck
