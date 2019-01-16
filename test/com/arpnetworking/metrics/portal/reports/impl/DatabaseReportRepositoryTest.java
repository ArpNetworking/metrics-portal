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
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule;
import models.ebean.ReportExecution;
import models.internal.Organization;
import models.internal.impl.ChromeScreenshotReportSource;
import models.internal.impl.DefaultReport;
import models.internal.impl.DefaultReportResult;
import models.internal.reports.RecipientGroup;
import models.internal.reports.Report;
import models.internal.reports.ReportSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.WithApplication;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceException;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringWhiteSpace;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

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
        assertFalse(_repository.getReport(uuid, Organization.DEFAULT).isPresent());
    }

    @Test
    public void testCreateNewReport() {
        final Report report = TestBeanFactory.createEbeanReport().toInternal();
        _repository.addOrUpdateReport(report, Organization.DEFAULT);
        final Report retrievedReport = _repository.getReport(report.getId(), Organization.DEFAULT).get();

        assertThat("ids should match", retrievedReport.getId(), equalTo(report.getId()));
        assertThat("names should match", retrievedReport.getName(), equalTo(report.getName()));
        assertThat("sources should match", retrievedReport.getSource(), equalTo(report.getSource()));
        assertThat("schedule should match", retrievedReport.getSchedule(), equalTo(report.getSchedule()));

        // We need to convert this to an array first to resolve `contains` to its varargs overload.
        // If we do not, Hamcrest will attempt to match against an Iterable<Collection<_>>, which is not what we want.
        final RecipientGroup[] groups = report.getRecipientGroups().toArray(new RecipientGroup[] {});
        assertThat("groups should match", retrievedReport.getRecipientGroups(), contains(groups));
    }

//    @Test
//    public void testUpdateExistingReport() {
//        // FIXME(cbriones): Persist neverschedule?
//        final Schedule schedule = new OneOffSchedule.Builder()
//                .setRunAtAndAfter(Instant.now())
//                .setRunUntil(Instant.now().plus(Duration.ofHours(1)))
//                .build();
//
//        final DefaultReport.Builder reportBuilder = TestBeanFactory.createReportBuilder().setSchedule(schedule);
//        final Report report = reportBuilder.setName(ORIGINAL_REPORT_NAME).build();
//        _repository.addOrUpdateReport(report, Organization.DEFAULT);
//        assertThat(report.getName(), equalTo(ORIGINAL_REPORT_NAME));
//
//        final Report updatedReport = reportBuilder.setName(ALTERED_REPORT_NAME).build();
//        _repository.addOrUpdateReport(updatedReport, Organization.DEFAULT);
//        final Optional<String> updatedName = _repository.getReport(report.getId(), Organization.DEFAULT).map(Report::getName);
//        assertThat(updatedName, equalTo(Optional.of(ALTERED_REPORT_NAME)));
//    }

//    @Test
//    public void testUpdateRecipientGroups() {
//        final DefaultReport.Builder reportBuilder = TestBeanFactory.createReportBuilder();
//
//        // Initial report
//        final Report report = reportBuilder.build();
//        _repository.addOrUpdateReport(reportBuilder.build(), Organization.DEFAULT);
//
//        // Update the recipients
//        final RecipientGroup newGroup = new EmailRecipientGroup.Builder()
//                .setId(UUID.randomUUID())
//                .setEmails(Collections.singleton("somenewemail@test.com"))
//                .setFormats(Collections.singletonList(HtmlReportFormat.getInstance()))
//                .setName("New recipient group")
//                .build();
//        final Report updatedReport = reportBuilder.setRecipientGroups(Collections.singleton(newGroup)).build();
//        _repository.addOrUpdateReport(updatedReport, Organization.DEFAULT);
//
//
//        final Report retrievedReport = _repository.getReport(report.getId(), Organization.DEFAULT).get();
//        assertThat(retrievedReport.getRecipientGroups(), hasSize(1));
//        assertThat(retrievedReport.getRecipientGroups(), contains(newGroup));
//    }
//
//    @Test
//    public void testUpdateSchedule() {
//        final DefaultReport.Builder reportBuilder = TestBeanFactory.createReportBuilder();
//        final PeriodicSchedule.Builder scheduleBuilder =
//                new PeriodicSchedule.Builder()
//                        .setRunAtAndAfter(Instant.now())
//                        .setRunUntil(null)
//                        .setOffset(Duration.ofHours(1))
//                        .setPeriod(ChronoUnit.DAYS)
//                        .setZone(ZoneId.systemDefault());
//
//        // Initial report
//        final Report report = reportBuilder.build();
//        _repository.addOrUpdateReport(reportBuilder.build(), Organization.DEFAULT);
//
//        // Update the schedule
//        final Schedule updatedSchedule = scheduleBuilder.setPeriod(ChronoUnit.HOURS).build();
//        reportBuilder.setSchedule(updatedSchedule);
//        _repository.addOrUpdateReport(reportBuilder.build(), Organization.DEFAULT);
//
//        final Report retrievedReport = _repository.getReport(report.getId(), Organization.DEFAULT).get();
//        assertThat(retrievedReport.getSchedule(), equalTo(updatedSchedule));
//    }

    @Test
    public void testIdempotentSave() {
        final Report report = TestBeanFactory.createEbeanReport().toInternal();
        _repository.addOrUpdateReport(report, Organization.DEFAULT);
        final UUID reportId = report.getId();
        final UUID sourceId = report.getSource().getId();
        _repository.addOrUpdateReport(report, Organization.DEFAULT);

        assertThat("Report ID should remain unchanged", reportId, equalTo(report.getId()));
        assertThat("Source ID should remain unchanged", sourceId, equalTo(report.getSource().getId()));
    }

//    @Test
//    public void testUpdateReportSource() {
//        final DefaultReport.Builder reportBuilder = TestBeanFactory.createReportBuilder();
//
//        final ChromeScreenshotReportSource.Builder sourceBuilder =
//                new ChromeScreenshotReportSource.Builder()
//                        .setId(UUID.randomUUID())
//                        .setTitle("Test title")
//                        .setTriggeringEventName("onload")
//                        .setUri(URI.create("https://foo.test.com"));
//
//        // Initial report
//        final Report report = reportBuilder.setReportSource(sourceBuilder.build()).build();
//        _repository.addOrUpdateReport(reportBuilder.build(), Organization.DEFAULT);
//
//        // Update the source
//        final ReportSource updatedSource = sourceBuilder.setTitle("updated title").build();
//        final Report updatedReport = reportBuilder.setReportSource(updatedSource).build();
//        _repository.addOrUpdateReport(updatedReport, Organization.DEFAULT);
//
//        final Report retrievedReport = _repository.getReport(report.getId(), Organization.DEFAULT).get();
//        assertThat(retrievedReport.getSource(), equalTo(updatedSource));
//    }

    @Test(expected = PersistenceException.class)
    public void testUnknownJobCompleted() {
        final Instant scheduled = Instant.now();
        _repository.jobSucceeded(UUID.randomUUID(), Organization.DEFAULT, scheduled, new TestReportResult(42));
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

        final Optional<Instant> lastRun = _repository.getLastRun(report.getId(), Organization.DEFAULT);
        assertThat(lastRun, equalTo(Optional.ofNullable(execution.getCompletedAt())));
    }

    @Test
    public void testJobFailed() {
        final Report report = TestBeanFactory.createEbeanReport().toInternal();
        _repository.addOrUpdateReport(report, Organization.DEFAULT);
        final Instant scheduled = Instant.now();

        final Throwable throwable = new RuntimeException("Whoops!");
        _repository.jobFailed(report.getId(), Organization.DEFAULT, scheduled, throwable);

        final ReportExecution execution = _repository.getExecution(report.getId(), Organization.DEFAULT, scheduled).get();
        assertThat(execution.getState(), equalTo(ReportExecution.State.FAILURE));
        assertThat(execution.getCompletedAt(), not(nullValue()));
        assertThat(execution.getReport(), not(nullValue()));
        assertThat(execution.getReport().getUuid(), equalTo(report.getId()));
        assertThat(execution.getResult(), nullValue());
        assertThat(execution.getError(), equalTo(throwable.toString()));

        final Optional<Instant> lastRun = _repository.getLastRun(report.getId(), Organization.DEFAULT);
        assertThat(lastRun, equalTo(Optional.empty()));
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

        final Optional<Instant> lastRun = _repository.getLastRun(report.getId(), Organization.DEFAULT);
        assertThat(lastRun, equalTo(Optional.empty()));
    }
}
