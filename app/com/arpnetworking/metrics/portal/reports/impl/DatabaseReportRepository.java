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

import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import io.ebean.Ebean;
import io.ebean.Transaction;
import models.ebean.ReportRecipientGroup;
import models.ebean.ReportSource;
import models.ebean.ReportingJob;

import javax.persistence.PersistenceException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class DatabaseReportRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseReportRepository.class);

    private AtomicBoolean isOpen = new AtomicBoolean(false);

    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening DatabaseReportRepository").log();
        isOpen.set(true);
    }

    public void close() {
        assertIsOpen();
        LOGGER.debug().setMessage("Closing DatabaseReportRepository").log();
        isOpen.set(false);
    }

    public Optional<ReportingJob> getJob(UUID identifier) {
        assertIsOpen();
        ReportingJob reportingJob =
            allJobs()
            .where()
            .eq("uuid", identifier)
            .findOne();
        return Optional.ofNullable(reportingJob);
    }

    public void deleteJob(ReportingJob job) {
        try {
            Ebean.delete(job);
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    public boolean addOrUpdateJob(ReportingJob job) {
        try (Transaction transaction = Ebean.beginTransaction()) {
            Optional<ReportingJob> existingJob = getJob(job.getUuid());
            boolean created = !existingJob.isPresent();
            Ebean.save(job);
            transaction.commit();
            return created;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    public boolean addOrUpdateRecipientGroup(ReportRecipientGroup group) {
        try (Transaction transaction = Ebean.beginTransaction()) {
            Optional<ReportRecipientGroup> existingSource =
                    Ebean.find(ReportRecipientGroup.class)
                        .where()
                        .eq("uuid", group.getUuid())
                        .findOne();
            boolean created = !existingSource.isPresent();
            Ebean.save(group);
            transaction.commit();
            return created;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    public boolean addOrUpdateSource(ReportSource source) {
        try (Transaction transaction = Ebean.beginTransaction()) {
            Optional<ReportSource> existingSource =
                    Ebean.find(ReportSource.class)
                            .where()
                            .eq("uuid", group.getUuid())
                            .findOne();
            boolean created = !existingSource.isPresent();
            Ebean.save(group);
            transaction.commit();
            return created;
        } catch (RuntimeException e) {
            throw new PersistenceException(e);
        }
    }

    private static io.ebean.Query<ReportingJob> allJobs() {
        return Ebean.find(ReportingJob.class);
    }

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(boolean expectedState) {
        if (isOpen.get() != expectedState) {
            throw new IllegalStateException(String.format("ReportingJob repository is not %s", expectedState ? "open" : "closed"));
        }
    }
}
