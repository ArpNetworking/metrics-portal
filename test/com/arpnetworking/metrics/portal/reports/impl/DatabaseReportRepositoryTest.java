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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.H2ConnectionStringFactory;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.scheduling.JobQuery;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule;
import com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.typesafe.config.ConfigFactory;
import models.ebean.ReportExecution;
import models.internal.Organization;
import models.internal.impl.ChromeScreenshotReportSource;
import models.internal.impl.DefaultReport;
import models.internal.impl.DefaultReportResult;
import models.internal.impl.HtmlReportFormat;
import models.internal.reports.Recipient;
import models.internal.reports.Report;
import models.internal.reports.ReportFormat;
import models.internal.reports.ReportSource;
import models.internal.scheduling.Job;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.WithApplication;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.persistence.PersistenceException;

/**
 * Unit test suite for {@link DatabaseReportRepository}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class DatabaseReportRepositoryTest extends WithApplication {

    private static final String ORIGINAL_REPORT_NAME = "Original Report Name";
    private static final String ALTERED_REPORT_NAME = "Altered Report Name";

    private DatabaseReportRepository _repository = new DatabaseReportRepository(new DatabaseReportRepository.GenericQueryGenerator());

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
                .loadConfig(ConfigFactory.load("portal.application.conf"))
                .configure("reportRepository.type", DatabaseReportRepository.class.getName())
                .configure("reportRepository.reportQueryGenerator.type", DatabaseReportRepository.GenericQueryGenerator.class.getName())
                .configure(AkkaClusteringConfigFactory.generateConfiguration())
                .configure(H2ConnectionStringFactory.generateConfiguration())
                .build();
    }

    @Test
    public void testGetReportWithInvalidId() {
        final UUID uuid = UUID.randomUUID();
        assertFalse(_repository.getReport(uuid, Organization.DEFAULT).isPresent());
    }

    @Test
    public void testCreateNewReport() {
        final Report report = TestBeanFactory.createReportBuilder().build();
        _repository.addOrUpdateReport(report, Organization.DEFAULT);
        final Report retrievedReport = _repository.getReport(report.getId(), Organization.DEFAULT).get();

        assertThat("ids should match", retrievedReport.getId(), equalTo(report.getId()));
        assertThat("names should match", retrievedReport.getName(), equalTo(report.getName()));
        assertThat("sources should match", retrievedReport.getSource(), equalTo(report.getSource()));
        assertThat("schedule should match", retrievedReport.getSchedule(), equalTo(report.getSchedule()));

        final ImmutableMap<ReportFormat, Collection<Recipient>> recipientsByFormat = report.getRecipientsByFormat();
        assertThat("recipients should match", retrievedReport.getRecipientsByFormat(), equalTo(recipientsByFormat));
    }

    @Test
    public void testUpdateExistingReport() {
        final Schedule schedule = new OneOffSchedule.Builder()
                .setRunAtAndAfter(Instant.now())
                .setRunUntil(Instant.now().plus(Duration.ofHours(1)))
                .build();

        final DefaultReport.Builder reportBuilder = TestBeanFactory.createReportBuilder().setSchedule(schedule);
        final Report report = reportBuilder.setName(ORIGINAL_REPORT_NAME).build();
        _repository.addOrUpdateReport(report, Organization.DEFAULT);
        assertThat(report.getName(), equalTo(ORIGINAL_REPORT_NAME));

        final Report updatedReport = reportBuilder.setName(ALTERED_REPORT_NAME).build();
        _repository.addOrUpdateReport(updatedReport, Organization.DEFAULT);
        final Optional<String> updatedName = _repository.getReport(report.getId(), Organization.DEFAULT).map(Report::getName);
        assertThat(updatedName, equalTo(Optional.of(ALTERED_REPORT_NAME)));
    }

    @Test
    public void testUpdateRecipients() {
        final DefaultReport.Builder reportBuilder = TestBeanFactory.createReportBuilder();

        // Initial report
        final Report report = reportBuilder.build();
        _repository.addOrUpdateReport(reportBuilder.build(), Organization.DEFAULT);

        // Update the recipients
        final ReportFormat format = new HtmlReportFormat.Builder().build();
        final Recipient recipient = TestBeanFactory.createRecipient();
        final Report updatedReport = reportBuilder
                .setRecipients(ImmutableSetMultimap.of(format, recipient))
                .build();
        _repository.addOrUpdateReport(updatedReport, Organization.DEFAULT);

        final Report retrievedReport = _repository.getReport(report.getId(), Organization.DEFAULT).get();
        final Set<Recipient> allRecipients =
                retrievedReport.getRecipientsByFormat()
                        .values()
                        .stream()
                        .flatMap(Collection::stream)
                        .collect(Collectors.toSet());

        assertThat(allRecipients, hasSize(1));
        assertThat(allRecipients, contains(recipient));
        assertThat(retrievedReport.getRecipientsByFormat().get(format), contains(recipient));
    }

    @Test
    public void testUpdateSchedule() {
        final DefaultReport.Builder reportBuilder = TestBeanFactory.createReportBuilder();
        final PeriodicSchedule.Builder scheduleBuilder =
                new PeriodicSchedule.Builder()
                        .setRunAtAndAfter(Instant.now())
                        .setRunUntil(null)
                        .setOffset(Duration.ofMinutes(10))
                        .setPeriod(ChronoUnit.DAYS)
                        .setZone(ZoneId.systemDefault());

        // Initial report
        final Report report = reportBuilder.build();
        _repository.addOrUpdateReport(reportBuilder.build(), Organization.DEFAULT);

        // Update the schedule
        final Schedule updatedSchedule = scheduleBuilder.setPeriod(ChronoUnit.HOURS).build();
        reportBuilder.setSchedule(updatedSchedule);
        _repository.addOrUpdateReport(reportBuilder.build(), Organization.DEFAULT);

        final Report retrievedReport = _repository.getReport(report.getId(), Organization.DEFAULT).get();
        assertThat(retrievedReport.getSchedule(), equalTo(updatedSchedule));
    }

    @Test
    public void testUpdateReportSource() {
        final DefaultReport.Builder reportBuilder = TestBeanFactory.createReportBuilder();

        final ChromeScreenshotReportSource.Builder sourceBuilder =
                new ChromeScreenshotReportSource.Builder()
                        .setId(UUID.randomUUID())
                        .setTitle("Test title")
                        .setTriggeringEventName("onload")
                        .setUri(URI.create("https://foo.test.com"));

        // Initial report
        final Report report = reportBuilder.setReportSource(sourceBuilder.build()).build();
        _repository.addOrUpdateReport(reportBuilder.build(), Organization.DEFAULT);

        // Update the source
        final ReportSource updatedSource = sourceBuilder.setTitle("updated title").build();
        final Report updatedReport = reportBuilder.setReportSource(updatedSource).build();
        _repository.addOrUpdateReport(updatedReport, Organization.DEFAULT);

        final Report retrievedReport = _repository.getReport(report.getId(), Organization.DEFAULT).get();
        assertThat(retrievedReport.getSource(), equalTo(updatedSource));
    }

    @Test(expected = PersistenceException.class)
    public void testUnknownJobCompleted() {
        final Instant scheduled = Instant.now();
        final Report.Result result = new DefaultReportResult();
        _repository.jobSucceeded(UUID.randomUUID(), Organization.DEFAULT, scheduled, result);
    }

    @Test
    public void testJobSucceeded() {
        final Report report = TestBeanFactory.createEbeanReport().toInternal();
        _repository.addOrUpdateReport(report, Organization.DEFAULT);
        final Instant scheduled = Instant.now();

        final Report.Result result = new DefaultReportResult();
        _repository.jobSucceeded(report.getId(), Organization.DEFAULT, scheduled, result);

        final ReportExecution execution = _repository.getExecution(report.getId(), Organization.DEFAULT, scheduled).get();
        assertThat(execution.getState(), equalTo(ReportExecution.State.SUCCESS));
        assertThat(execution.getCompletedAt(), not(nullValue()));
        assertThat(execution.getReport(), not(nullValue()));
        assertThat(execution.getReport().getUuid(), equalTo(report.getId()));
        assertThat(execution.getResult(), not(nullValue()));
        assertThat(execution.getError(), nullValue());
        assertThat(execution.getScheduled(), equalTo(scheduled));

        final Optional<Instant> lastRun = _repository.getLastRun(report.getId(), Organization.DEFAULT);
        assertThat(lastRun, equalTo(Optional.ofNullable(execution.getCompletedAt())));
    }

    @Test
    public void testJobFailed() {
        final Report report = TestBeanFactory.createEbeanReport().toInternal();
        _repository.addOrUpdateReport(report, Organization.DEFAULT);
        final Instant scheduled = Instant.now();

        final Throwable throwable = new IllegalStateException("Whoops!", new RuntimeException("the cause"));
        _repository.jobFailed(report.getId(), Organization.DEFAULT, scheduled, throwable);

        final ReportExecution execution = _repository.getExecution(report.getId(), Organization.DEFAULT, scheduled).get();
        assertThat(execution.getState(), equalTo(ReportExecution.State.FAILURE));
        assertThat(execution.getCompletedAt(), not(nullValue()));
        assertThat(execution.getReport(), not(nullValue()));
        assertThat(execution.getReport().getUuid(), equalTo(report.getId()));
        assertThat(execution.getResult(), nullValue());
        assertThat(execution.getScheduled(), equalTo(scheduled));

        final String retrievedError = execution.getError();
        assertThat(retrievedError, notNullValue());
        assertThat(retrievedError, containsString(throwable.getMessage()));
        assertThat(retrievedError, containsString(throwable.getCause().getMessage()));
        final Optional<Instant> lastRun = _repository.getLastRun(report.getId(), Organization.DEFAULT);
        assertThat(lastRun, equalTo(Optional.ofNullable(execution.getCompletedAt())));
    }

    @Test
    public void testJobStarted() {
        final Report report = TestBeanFactory.createEbeanReport().toInternal();
        _repository.addOrUpdateReport(report, Organization.DEFAULT);
        final Instant scheduled = Instant.now();

        _repository.jobStarted(report.getId(), Organization.DEFAULT, scheduled);

        final ReportExecution execution = _repository.getExecution(report.getId(), Organization.DEFAULT, scheduled).get();
        assertThat(execution.getState(), equalTo(ReportExecution.State.STARTED));
        assertThat(execution.getCompletedAt(), nullValue());
        assertThat(execution.getStartedAt(), not(nullValue()));
        assertThat(execution.getResult(), nullValue());
        assertThat(execution.getError(), nullValue());
        assertThat(execution.getReport(), not(nullValue()));
        assertThat(execution.getReport().getUuid(), equalTo(report.getId()));
        assertThat(execution.getScheduled(), equalTo(scheduled));

        final Optional<Instant> lastRun = _repository.getLastRun(report.getId(), Organization.DEFAULT);
        assertThat(lastRun, equalTo(Optional.empty()));
    }

    @Test
    public void testStateChangeClearsFields() {
        final Report report = TestBeanFactory.createEbeanReport().toInternal();
        _repository.addOrUpdateReport(report, Organization.DEFAULT);
        final Instant scheduled = Instant.now();

        _repository.jobStarted(report.getId(), Organization.DEFAULT, scheduled);
        _repository.jobSucceeded(report.getId(), Organization.DEFAULT, scheduled, new DefaultReportResult());

        // A succeeded updated should *not* clear the start time
        ReportExecution execution = _repository.getExecution(report.getId(), Organization.DEFAULT, scheduled).get();
        assertThat(execution.getStartedAt(), notNullValue());
        assertThat(execution.getResult(), notNullValue());
        assertThat(execution.getCompletedAt(), notNullValue());
        assertThat(execution.getError(), nullValue());

        // A failed updated should *not* clear the start time but it should clear the result
        _repository.jobFailed(report.getId(), Organization.DEFAULT, scheduled, new IllegalStateException("whoops!"));
        execution = _repository.getExecution(report.getId(), Organization.DEFAULT, scheduled).get();
        assertThat(execution.getStartedAt(), notNullValue());
        assertThat(execution.getResult(), nullValue());
        assertThat(execution.getCompletedAt(), notNullValue());
        assertThat(execution.getError(), notNullValue());
    }

    @Test
    public void testQueryAllReports() {
        final List<Report> reports = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            final Report report =
                    TestBeanFactory.createReportBuilder()
                            .setName("test report #" + i)
                            .build();
            _repository.addOrUpdateReport(report, Organization.DEFAULT);
            reports.add(report);
        }
        final Report[] reportsArray = new Report[reports.size()];
        reports.toArray(reportsArray);

        final JobQuery<Report.Result> query = _repository.createQuery(Organization.DEFAULT);

        final List<? extends Job<Report.Result>> results = query.execute().values();
        assertThat(results, hasSize(5));
        assertThat(results, containsInAnyOrder(reportsArray));
    }

    @Test
    public void testQueryClauseWithOffset() {
        final int reportCount = 5;
        final int testOffset = 2;
        for (int i = 0; i < reportCount; i++) {
            final Report report = TestBeanFactory.createReportBuilder().build();
            _repository.addOrUpdateReport(report, Organization.DEFAULT);
        }

        final JobQuery<Report.Result> baseQuery = _repository.createQuery(Organization.DEFAULT);

        final List<? extends Job<Report.Result>> results = baseQuery.offset(testOffset).execute().values();
        assertThat(results, hasSize(reportCount - testOffset));

        final List<? extends Job<Report.Result>> emptyResults = baseQuery.offset(reportCount).execute().values();
        assertThat(emptyResults, empty());
    }

    @Test
    public void testQueryClauseWithLimit() {
        final int reportCount = 5;
        for (int i = 0; i < reportCount; i++) {
            final Report report = TestBeanFactory.createReportBuilder().build();
            _repository.addOrUpdateReport(report, Organization.DEFAULT);
        }

        final JobQuery<Report.Result> query = _repository.createQuery(Organization.DEFAULT);

        final List<? extends Job<Report.Result>> results = query.limit(1).execute().values();
        assertThat(results, hasSize(1));

        final List<? extends Job<Report.Result>> allResults = query.limit(reportCount * 2).execute().values();
        assertThat(allResults, hasSize(reportCount));
    }

    @Test
    public void testQueryClauseWithOffsetAndLimit() {
        final int reportCount = 10;
        final int testOffset = 3;
        final int testLimit = 2;
        for (int i = 0; i < reportCount; i++) {
            final Report report = TestBeanFactory.createReportBuilder().build();
            _repository.addOrUpdateReport(report, Organization.DEFAULT);
        }

        final JobQuery<Report.Result> query = _repository.createQuery(Organization.DEFAULT);

        final List<? extends Job<Report.Result>> results = query.offset(testOffset).limit(testLimit).execute().values();
        assertThat(results, hasSize(testLimit));

        final List<? extends Job<Report.Result>> singleResult = query.offset(reportCount - 1).limit(testLimit).execute().values();
        assertThat(singleResult, hasSize(1));

        final List<? extends Job<Report.Result>> emptyResults = query.offset(reportCount).limit(reportCount).execute().values();
        assertThat(emptyResults, empty());
    }

    @Test
    public void testQueryAllReportsReturnsNothing() {
        final JobQuery<Report.Result> query = _repository.createQuery(Organization.DEFAULT);
        final List<? extends Job<Report.Result>> results = query.execute().values();
        assertThat(results, empty());
    }
}
