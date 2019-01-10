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
import models.ebean.PeriodicReportSchedule;
import models.ebean.ReportRecipient;
import models.ebean.ReportRecipientGroup;
import models.ebean.ReportSource;
import models.ebean.Report;
import models.ebean.ReportExecution;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.WithApplication;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.persistence.PersistenceException;

/**
 * Unit test suite for {@link DatabaseReportRepository}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class DatabaseReportRepositoryTest extends WithApplication {

    private static final String ORIGINAL_REPORT_NAME = "Original Report Name";
    private static final String ALTERED_REPORT_NAME = "Altered Report Name";

    private final DatabaseReportRepository _repository = new DatabaseReportRepository();

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
    public void testGetReportWithInvalidId() {
        final UUID uuid = UUID.randomUUID();
        assertFalse(_repository.getReport(uuid).isPresent());
    }

    @Test
    public void testCreateNewReport() {
        final Report report = newReport();
        _repository.addOrUpdateReport(report);

        final ReportRecipientGroup group = report.getRecipientGroups().stream().findFirst().get();

        assertThat(report.getUpdatedAt(), not(nullValue()));
        assertThat(report.getCreatedAt(), not(nullValue()));
        assertThat("schedule should have been created", report.getSchedule().getId(), not(nullValue()));
        assertThat("report source should have been created", report.getReportSource().getId(), not(nullValue()));
        assertThat("recipient group should have been created", group.getId(), not(nullValue()));
    }

    @Test
    public void testUpdateExistingReport() {
        final Report report = newReport();

        report.setName(ORIGINAL_REPORT_NAME);
        _repository.addOrUpdateReport(report);

        report.setName(ALTERED_REPORT_NAME);
        _repository.addOrUpdateReport(report);

        final Optional<String> updatedName = _repository.getReport(report.getUuid()).map(Report::getName);
        assertThat(updatedName, equalTo(Optional.of(ALTERED_REPORT_NAME)));
    }

    @Test
    public void testUpdateExistingReportingSource() {
        final Report report = newReport();
        _repository.addOrUpdateReport(report);
        final ReportSource reportSource = report.getReportSource();
        _repository.addOrUpdateReport(report);

        final Report retrievedReport = _repository.getReport(report.getUuid()).get();
        assertThat(retrievedReport.getReportSource().getUuid(), equalTo(reportSource.getUuid()));
        Assert.fail("This doesn't update anything, fix that.");
    }

    @Test
    public void testUpdateExistingReportingGroup() {
        final Report report = newReport();
        _repository.addOrUpdateReport(report);

        final ReportRecipientGroup group = report.getRecipientGroups().stream().findFirst().get();
        final List<ReportRecipient> newRecipients = new ArrayList<>(group.getRecipients());
        newRecipients.add(ReportRecipient.newEmailRecipient("some-new-email@test.com"));
        group.setRecipients(newRecipients);
        _repository.addOrUpdateReport(report);

        final Report retrievedReport = _repository.getReport(report.getUuid()).get();
        final ReportRecipientGroup retrievedGroup = retrievedReport.getRecipientGroups().stream().findFirst().get();

        assertThat(retrievedGroup.getUuid(), equalTo(group.getUuid()));
        assertThat(retrievedGroup.getName(), equalTo(group.getName()));
        assertThat(retrievedGroup.getRecipients(), equalTo(newRecipients));
    }

    @Test
    public void testUpdateSchedule() {
        final Report report = newReport();
        _repository.addOrUpdateReport(report);

        final PeriodicReportSchedule periodicSchedule = new PeriodicReportSchedule();
        periodicSchedule.setStartDate(Date.valueOf("2018-12-01"));
        periodicSchedule.setOffset(Duration.ofHours(2));
        periodicSchedule.setEndDate(null);

        report.setSchedule(periodicSchedule);
        _repository.addOrUpdateReport(report);

        final Report retrievedReport = _repository.getReport(report.getUuid()).get();
        assertThat(retrievedReport.getSchedule(), equalTo(periodicSchedule));
    }

    @Test
    public void testUpdateExistingReportWithNewSource() {
        final Report report = newReport();
        _repository.addOrUpdateReport(report);
        final ReportSource reportSource = new ChromeScreenshotReportSource();
        reportSource.setUuid(UUID.randomUUID());
        report.setReportSource(reportSource);
        _repository.addOrUpdateReport(report);

        final Report retrievedReport = _repository.getReport(report.getUuid()).get();
        assertThat(retrievedReport.getReportSource().getUuid(), equalTo(reportSource.getUuid()));
    }

    @Test(expected = PersistenceException.class)
    public void testCreateReportWithoutASchedule() {
        final Report report = newReport();
        // Intentionally setting report source to null for test.
        //noinspection ConstantConditions
        report.setSchedule(null);
        _repository.addOrUpdateReport(report);
        System.out.println(report);
    }

    @Test(expected = PersistenceException.class)
    public void testCreateReportWithoutASource() {
        final Report report = newReport();
        // Intentionally setting report source to null for test.
        //noinspection ConstantConditions
        report.setReportSource(null);
        _repository.addOrUpdateReport(report);
    }

    @Test
    public void testJobCompleted() {
        final Report report = newReport();
        _repository.addOrUpdateReport(report);
        final Instant now = Instant.now();

        final Report.State state = Report.State.SUCCESS;
        _repository.jobCompleted(report, state, now);

        final ReportExecution execution = _repository.getMostRecentExecution(report).get();

        assertThat(execution.getState(), equalTo(state));
        assertThat(execution.getStartedAt(), equalTo(now));
    }

    private Report newReport() {
        final UUID sourceUuid = UUID.randomUUID();

        final ReportRecipientGroup group = TestBeanFactory.createEbeanReportRecipientGroup();
        final ReportSource source = new ChromeScreenshotReportSource();
        source.setUuid(sourceUuid);
        final Report report = TestBeanFactory.createEbeanReport();
        report.setReportSource(source);
        report.setRecipientGroups(Collections.singleton(group));
        return report;
    }
}
