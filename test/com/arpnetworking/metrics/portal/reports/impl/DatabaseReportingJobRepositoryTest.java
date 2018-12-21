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

import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.H2ConnectionStringFactory;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import models.ebean.ChromeScreenshotReportSource;
import models.ebean.RecurringReportingSchedule;
import models.ebean.ReportRecipient;
import models.ebean.ReportRecipientGroup;
import models.ebean.ReportSource;
import models.ebean.ReportingJob;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
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
import java.util.UUID;
import javax.persistence.PersistenceException;

/**
 * Put a real doc here before committing.
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
        Assert.assertFalse(_repository.getJob(uuid).isPresent());
    }

    @Test
    public void testCreateNewJob() {
        final ReportingJob job = newJob();
        final ReportRecipientGroup group = TestBeanFactory.createEbeanReportRecipientGroup();
        job.setRecipientGroups(Collections.singleton(group));
        _repository.addOrUpdateJob(job);

        Assert.assertThat(job.getUpdatedAt(), CoreMatchers.not(CoreMatchers.nullValue()));
        Assert.assertThat(job.getCreatedAt(), CoreMatchers.not(CoreMatchers.nullValue()));
        Assert.assertThat("schedule should have been created",
                job.getSchedule().getId(), CoreMatchers.not(CoreMatchers.nullValue()));
        Assert.assertThat("report source should have been created",
                job.getReportSource().getId(), CoreMatchers.not(CoreMatchers.nullValue()));
        Assert.assertThat("recipient group should have been created",
                group.getId(), CoreMatchers.not(CoreMatchers.nullValue()));
    }

    @Test
    public void testUpdateExistingJob() {
        final ReportingJob job = newJob();

        job.setName(ORIGINAL_JOB_NAME);
        _repository.addOrUpdateJob(job);

        job.setName(ALTERED_JOB_NAME);
        _repository.addOrUpdateJob(job);

        final ReportingJob optUpdatedJob = _repository.getJob(job.getUuid()).get();
        Assert.assertThat(optUpdatedJob.getName(), CoreMatchers.equalTo(ALTERED_JOB_NAME));
    }

    @Test
    public void testUpdateExistingReportingSource() {
        final ReportingJob job = newJob();
        _repository.addOrUpdateJob(job);
        final ReportSource reportSource = job.getReportSource();
        reportSource.setTimeoutInSeconds(424242L);
        _repository.addOrUpdateJob(job);

        final ReportingJob retrievedJob = _repository.getJob(job.getUuid()).get();
        Assert.assertThat(retrievedJob.getReportSource().getUuid(), CoreMatchers.equalTo(reportSource.getUuid()));
        Assert.assertThat(retrievedJob.getReportSource().getTimeoutInSeconds(), CoreMatchers.equalTo(reportSource.getTimeoutInSeconds()));
        Assert.assertThat(retrievedJob.getReportSource().getTimeoutInSeconds(), CoreMatchers.equalTo(424242L));
    }

    @Test
    public void testUpdateExistingReportingGroup() {
        final ReportingJob job = newJob();
        final ReportRecipientGroup group = TestBeanFactory.createEbeanReportRecipientGroup();
        job.setRecipientGroups(Collections.singleton(group));
        _repository.addOrUpdateJob(job);

        final List<ReportRecipient> newRecipients = new ArrayList<>(group.getRecipients());
        newRecipients.add(ReportRecipient.newEmailRecipient("some-new-email@test.com"));
        group.setRecipients(newRecipients);
        _repository.addOrUpdateJob(job);

        final ReportingJob retrievedJob = _repository.getJob(job.getUuid()).get();
        final ReportRecipientGroup retrievedGroup = retrievedJob.getRecipientGroups().stream().findFirst().get();

        Assert.assertThat(retrievedGroup.getUuid(), CoreMatchers.equalTo(group.getUuid()));
        Assert.assertThat(retrievedGroup.getName(), CoreMatchers.equalTo(group.getName()));
        Assert.assertThat(retrievedGroup.getRecipients(), CoreMatchers.equalTo(newRecipients));
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
        Assert.assertThat(retrievedJob.getSchedule(), CoreMatchers.equalTo(recurringSchedule));
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
        Assert.assertThat(retrievedJob.getReportSource().getUuid(), CoreMatchers.equalTo(reportSource.getUuid()));
    }

    @Test(expected = PersistenceException.class)
    public void testCreateJobWithoutASchedule() {
        final ReportingJob job = newJob();
        job.setSchedule(null);
        _repository.addOrUpdateJob(job);
        System.out.println(job);
    }

    @Test(expected = PersistenceException.class)
    public void testCreateJobWithoutASource() {
        final ReportingJob job = newJob();
        job.setReportSource(null);
        _repository.addOrUpdateJob(job);
    }

    @Test
    public void testDeleteJob() {
        final ReportingJob job = newJob();
        _repository.addOrUpdateJob(job);
        _repository.deleteJob(job.getUuid());
        Assert.assertFalse(_repository.getJob(job.getUuid()).isPresent());
    }

    private ReportingJob newJob() {
        final UUID sourceUuid = UUID.randomUUID();

        final ReportRecipientGroup group = TestBeanFactory.createEbeanReportRecipientGroup();
        final ReportSource source = new ChromeScreenshotReportSource();
        source.setUuid(sourceUuid);
        final ReportingJob job = TestBeanFactory.createEbeanReportingJob();
        job.setReportSource(source);
//        job.setRecipientGroup(group);
        return job;
    }
}
