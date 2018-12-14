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
import models.ebean.ReportRecipientGroup;
import models.ebean.ReportSource;
import models.ebean.ReportingJob;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.WithApplication;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import org.junit.Assert;

import javax.persistence.PersistenceException;

import static org.junit.Assert.assertThat;

public class DatabaseReportRepositoryTest extends WithApplication {

    private static final String ORIGINAL_JOB_NAME = "Original Job Name";
    private static final String ALTERED_JOB_NAME = "Altered Job Name";

    private final DatabaseReportRepository repository = new DatabaseReportRepository();

    @Before
    public void setup() {
        repository.open();
    }

    @After
    public void teardown() {
        repository.close();
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
        Assert.assertFalse(repository.getJob(uuid).isPresent());
    }

    @Test
    public void testCreateNewJob() {
        ReportingJob job = newJob();
        boolean added = repository.addOrUpdateJob(job);
        Assert.assertTrue(added);
        assertThat(job.getUpdatedAt(), not(nullValue()));
        assertThat(job.getCreatedAt(), not(nullValue()));
        assertThat("schedule should have been created", job.getSchedule().getId(), not(nullValue()));
        assertThat(job.getReportSource().getId(), not(nullValue()));
    }

    @Test
    public void testUpdateExistingJob() {
        ReportingJob job = newJob();
        job.setName(ORIGINAL_JOB_NAME);
        boolean added = repository.addOrUpdateJob(job);
        Assert.assertTrue(added);

        job.setName(ALTERED_JOB_NAME);
        Assert.assertFalse(repository.addOrUpdateJob(job));

        Optional<ReportingJob> optUpdatedJob = repository.getJob(job.getUuid());
        assertThat(optUpdatedJob.get().getName(), equalTo(ALTERED_JOB_NAME));
    }

    @Test(expected = PersistenceException.class)
    public void testCreateJobWithoutASchedule() {
        ReportingJob job = newJob();
        job.setSchedule(null);
        repository.addOrUpdateJob(job);
        System.out.println(job);
    }

    @Test(expected = PersistenceException.class)
    public void testCreateJobWithoutASource() {
        ReportingJob job = newJob();
        job.setReportSource(null);
        repository.addOrUpdateJob(job);
    }

    @Test(expected = PersistenceException.class)
    public void testCreateJobWithoutARecipientGroup() {
        ReportingJob job = newJob();
        job.setRecipientGroup(null);
        repository.addOrUpdateJob(job);
    }

    @Test
    public void testDeleteJob() {
        ReportingJob job = newJob();
        boolean added = repository.addOrUpdateJob(job);
        Assert.assertTrue(added);

        repository.deleteJob(job);
        Assert.assertFalse(repository.getJob(job.getUuid()).isPresent());
    }

    private ReportingJob newJob() {
        final UUID sourceUuid = UUID.randomUUID();

        ReportRecipientGroup group = TestBeanFactory.createEbeanReportRecipientGroup();
        repository.addOrUpdateRecipientGroup(group);

        ReportSource source = new ChromeScreenshotReportSource();
        source.setUuid(sourceUuid);
        repository.addOrUpdateSource(source);

        ReportingJob job = TestBeanFactory.createEbeanReportingJob();
        job.setReportSource(source);
        job.setRecipientGroup(group);
        return job;
    }
}
