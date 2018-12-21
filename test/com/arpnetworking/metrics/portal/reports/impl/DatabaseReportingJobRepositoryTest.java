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

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.hamcrest.CoreMatchers.equalTo;

import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.H2ConnectionStringFactory;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import models.ebean.ChromeScreenshotReportSource;
import models.ebean.RecurringReportingSchedule;
import models.ebean.ReportRecipient;
import models.ebean.ReportRecipientGroup;
import models.ebean.ReportSource;
import models.ebean.ReportingJob;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.WithApplication;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.persistence.PersistenceException;

/**
 * Put a real doc here before committing.
 *
 * @author Christian Briones
 */
public class DatabaseReportingJobRepositoryTest extends WithApplication {

    private static final String ORIGINAL_JOB_NAME = "Original Job Name";
    private static final String ALTERED_JOB_NAME = "Altered Job Name";

    private final DatabaseReportingJobRepository _repository = new DatabaseReportingJobRepository();

    @Before
    public void setup() {
        _repository.open();
    }

    @After
    public void teardown() {
        _repository.close();
    }

    @Override
    public Application provideApplication() {
        return new GuiceApplicationBuilder()
                .configure(AkkaClusteringConfigFactory.generateConfiguration())
                .configure(H2ConnectionStringFactory.generateConfiguration())
                .build();
    }

    @Test
    public void testGetJobWithInvalidId() {
        final UUID uuid = UUID.randomUUID();
        assertFalse(_repository.getJob(uuid).isPresent());
    }

    @Test
    public void testCreateNewJob() {
        final ReportingJob job = newJob();
        _repository.addOrUpdateJob(job);

        final ReportRecipientGroup group = job.getRecipientGroups().stream().findFirst().get();

        assertThat(job.getUpdatedAt(), not(nullValue()));
        assertThat(job.getCreatedAt(), not(nullValue()));
        assertThat("schedule should have been created", job.getSchedule().getId(), not(nullValue()));
        assertThat("report source should have been created", job.getReportSource().getId(), not(nullValue()));
        assertThat("recipient group should have been created", group.getId(), not(nullValue()));
    }

    @Test
    public void testUpdateExistingJob() {
        final ReportingJob job = newJob();

        job.setName(ORIGINAL_JOB_NAME);
        _repository.addOrUpdateJob(job);

        job.setName(ALTERED_JOB_NAME);
        _repository.addOrUpdateJob(job);

        final Optional<String> updatedName = _repository.getJob(job.getUuid()).map(ReportingJob::getName);
        assertThat(updatedName, equalTo(Optional.of(ALTERED_JOB_NAME)));
    }

    @Test
    public void testUpdateExistingReportingSource() {
        final ReportingJob job = newJob();
        _repository.addOrUpdateJob(job);
        final ReportSource reportSource = job.getReportSource();
        reportSource.setTimeoutInSeconds(424242L);
        _repository.addOrUpdateJob(job);

        final ReportingJob retrievedJob = _repository.getJob(job.getUuid()).get();
        assertThat(retrievedJob.getReportSource().getUuid(), equalTo(reportSource.getUuid()));
        assertThat(retrievedJob.getReportSource().getTimeoutInSeconds(), equalTo(reportSource.getTimeoutInSeconds()));
        assertThat(retrievedJob.getReportSource().getTimeoutInSeconds(), equalTo(424242L));
    }

    @Test
    public void testUpdateExistingReportingGroup() {
        final ReportingJob job = newJob();
        _repository.addOrUpdateJob(job);

        final ReportRecipientGroup group = job.getRecipientGroups().stream().findFirst().get();
        final List<ReportRecipient> newRecipients = new ArrayList<>(group.getRecipients());
        newRecipients.add(ReportRecipient.newEmailRecipient("some-new-email@test.com"));
        group.setRecipients(newRecipients);
        _repository.addOrUpdateJob(job);

        final ReportingJob retrievedJob = _repository.getJob(job.getUuid()).get();
        final ReportRecipientGroup retrievedGroup = retrievedJob.getRecipientGroups().stream().findFirst().get();

        assertThat(retrievedGroup.getUuid(), equalTo(group.getUuid()));
        assertThat(retrievedGroup.getName(), equalTo(group.getName()));
        assertThat(retrievedGroup.getRecipients(), equalTo(newRecipients));
    }

    @Test
    public void testUpdateSchedule() {
        final ReportingJob job = newJob();
        _repository.addOrUpdateJob(job);

        final RecurringReportingSchedule recurringSchedule = new RecurringReportingSchedule();
        recurringSchedule.setStartDate(Date.valueOf("2018-12-01"));
        recurringSchedule.setAvailableAt(Timestamp.from(Instant.now()));
        recurringSchedule.setEndDate(null);
        recurringSchedule.setMaxOccurrences(30);
        job.setSchedule(recurringSchedule);
        _repository.addOrUpdateJob(job);

        final ReportingJob retrievedJob = _repository.getJob(job.getUuid()).get();
        assertThat(retrievedJob.getSchedule(), equalTo(recurringSchedule));
    }

    @Test
    public void testUpdateExistingJobWithNewSource() {
        final ReportingJob job = newJob();
        _repository.addOrUpdateJob(job);
        final ReportSource reportSource = new ChromeScreenshotReportSource();
        reportSource.setUuid(UUID.randomUUID());
        job.setReportSource(reportSource);
        _repository.addOrUpdateJob(job);

        final ReportingJob retrievedJob = _repository.getJob(job.getUuid()).get();
        assertThat(retrievedJob.getReportSource().getUuid(), equalTo(reportSource.getUuid()));
    }

    @Test(expected = PersistenceException.class)
    public void testCreateJobWithoutASchedule() {
        final ReportingJob job = newJob();
        // Intentionally setting report source to null for test.
        //noinspection ConstantConditions
        job.setSchedule(null);
        _repository.addOrUpdateJob(job);
        System.out.println(job);
    }

    @Test(expected = PersistenceException.class)
    public void testCreateJobWithoutASource() {
        final ReportingJob job = newJob();
        // Intentionally setting report source to null for test.
        //noinspection ConstantConditions
        job.setReportSource(null);
        _repository.addOrUpdateJob(job);
    }

    private ReportingJob newJob() {
        final UUID sourceUuid = UUID.randomUUID();

        final ReportRecipientGroup group = TestBeanFactory.createEbeanReportRecipientGroup();
        final ReportSource source = new ChromeScreenshotReportSource();
        source.setUuid(sourceUuid);
        final ReportingJob job = TestBeanFactory.createEbeanReportingJob();
        job.setReportSource(source);
        job.setRecipientGroups(Collections.singleton(group));
        return job;
    }
}
