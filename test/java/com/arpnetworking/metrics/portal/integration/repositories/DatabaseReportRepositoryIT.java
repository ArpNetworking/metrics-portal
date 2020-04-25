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
package com.arpnetworking.metrics.portal.integration.repositories;

import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.integration.test.EbeanServerHelper;
import com.arpnetworking.metrics.portal.reports.impl.DatabaseReportRepository;
import com.arpnetworking.metrics.portal.scheduling.JobQuery;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import io.ebean.EbeanServer;
import models.ebean.ReportExecution;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultReport;
import models.internal.impl.HtmlReportFormat;
import models.internal.impl.WebPageReportSource;
import models.internal.reports.Recipient;
import models.internal.reports.Report;
import models.internal.reports.ReportFormat;
import models.internal.reports.ReportSource;
import models.internal.scheduling.Job;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for {@link DatabaseReportRepository}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 * @author Ville Koskela (vkoskela at dropbox dot com)
 */
public class DatabaseReportRepositoryIT {

    private static final String ORIGINAL_REPORT_NAME = "Original Report Name";
    private static final String ALTERED_REPORT_NAME = "Altered Report Name";

    private EbeanServer _server;
    private DatabaseReportRepository _repository;
    private Organization _organization;
    private models.ebean.Organization _ebeanOrganization;

    @Before
    public void setUp() {
        _server = EbeanServerHelper.getMetricsDatabase();
        _repository = new DatabaseReportRepository(_server);
        _repository.open();

        _ebeanOrganization = TestBeanFactory.createEbeanOrganization();
        _server.save(_ebeanOrganization);
        _organization = TestBeanFactory.organizationFrom(_ebeanOrganization);
    }

    @After
    public void tearDown() {
        _repository.close();
    }

    @Test
    public void testGetForNonexistentReportAndOrganizationId() {
        final UUID uuid = UUID.randomUUID();
        assertFalse(_repository.getReport(uuid, TestBeanFactory.organizationFrom(UUID.randomUUID())).isPresent());
    }

    @Test
    public void testGetForNonexistentOrganizationId() {
        final Report report = TestBeanFactory.createReportBuilder().build();
        _repository.addOrUpdateReport(report, _organization);

        assertFalse(_repository.getReport(report.getId(), TestBeanFactory.organizationFrom(UUID.randomUUID())).isPresent());
    }

    @Test
    public void testGetForNonexistentReportId() {
        assertFalse(_repository.getReport(UUID.randomUUID(), _organization).isPresent());
    }

    @Test
    public void testCreateNewReport() {
        final Report report = TestBeanFactory.createReportBuilder().build();
        _repository.addOrUpdateReport(report, _organization);

        final Optional<Report> result = _repository.getReport(report.getId(), _organization);
        assertTrue(result.isPresent());

        final Report retrievedReport = result.get();
        assertThat("ids should match", retrievedReport.getId(), equalTo(report.getId()));
        assertThat("names should match", retrievedReport.getName(), equalTo(report.getName()));
        assertThat("sources should match", retrievedReport.getSource(), equalTo(report.getSource()));
        assertThat("schedule should match", retrievedReport.getSchedule(), equalTo(report.getSchedule()));

        final ImmutableMap<ReportFormat, Collection<Recipient>> recipientsByFormat = report.getRecipientsByFormat();
        assertThat("recipients should match", retrievedReport.getRecipientsByFormat(), equalTo(recipientsByFormat));
    }

    @Test
    public void testDeleteExistingReport() {
        final Report report = TestBeanFactory.createReportBuilder().build();
        _repository.addOrUpdateReport(report, _organization);

        final int deleted = _repository.deleteReport(report.getId(), _organization);
        assertThat("The report should have been deleted", deleted, equalTo(1));

        final Optional<Report> result = _repository.getReport(report.getId(), _organization);
        assertThat("The report should not have been returned", result.orElse(null), nullValue());

        final int deletedAgain = _repository.deleteReport(report.getId(), _organization);
        assertThat("The report should have already been marked as deleted", deletedAgain, equalTo(0));
    }

    @Test
    public void testDeleteMissingReport() {
        final int deleted = _repository.deleteReport(UUID.randomUUID(), _organization);
        assertThat("No reports should have been deleted", deleted, equalTo(0));
    }

    @Test
    public void testUpdateName() {
        final DefaultReport.Builder reportBuilder = TestBeanFactory.createReportBuilder();
        final Report initialReport = reportBuilder.setName(ORIGINAL_REPORT_NAME).build();
        _repository.addOrUpdateReport(initialReport, _organization);

        final Report updatedReport = reportBuilder.setName(ALTERED_REPORT_NAME).build();
        _repository.addOrUpdateReport(updatedReport, _organization);

        final Optional<String> updatedName = _repository.getReport(initialReport.getId(), _organization).map(Report::getName);
        assertThat(updatedName, equalTo(Optional.of(ALTERED_REPORT_NAME)));
    }

    @Test
    public void testUpdateRecipients() {
        final DefaultReport.Builder reportBuilder = TestBeanFactory.createReportBuilder();

        // Initial report
        final Report report = reportBuilder.build();
        _repository.addOrUpdateReport(reportBuilder.build(), _organization);

        // Update the recipients
        final ReportFormat format = new HtmlReportFormat.Builder().build();
        final Recipient recipient = TestBeanFactory.createRecipientBuilder().build();
        final Report updatedReport = reportBuilder
                .setRecipients(ImmutableSetMultimap.of(format, recipient))
                .build();
        _repository.addOrUpdateReport(updatedReport, _organization);

        final Optional<Report> result = _repository.getReport(report.getId(), _organization);
        assertTrue(result.isPresent());

        final Report retrievedReport = result.get();
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
        _repository.addOrUpdateReport(reportBuilder.build(), _organization);

        // Update the schedule
        final Schedule updatedSchedule = scheduleBuilder.setPeriod(ChronoUnit.HOURS).build();
        reportBuilder.setSchedule(updatedSchedule);
        _repository.addOrUpdateReport(reportBuilder.build(), _organization);

        final Optional<Report> result = _repository.getReport(report.getId(), _organization);
        assertTrue(result.isPresent());

        final Report retrievedReport = result.get();
        assertThat(retrievedReport.getSchedule(), equalTo(updatedSchedule));
    }

    @Test
    public void testUpdateReportSource() {
        final DefaultReport.Builder reportBuilder = TestBeanFactory.createReportBuilder();

        final WebPageReportSource.Builder sourceBuilder =
                new WebPageReportSource.Builder()
                        .setId(UUID.randomUUID())
                        .setTitle("Test title")
                        .setUri(URI.create("https://foo.test.com"));

        // Initial report
        final Report report = reportBuilder.setReportSource(sourceBuilder.build()).build();
        _repository.addOrUpdateReport(reportBuilder.build(), _organization);

        // Update the source
        final ReportSource updatedSource = sourceBuilder.setTitle("updated title").build();
        final Report updatedReport = reportBuilder.setReportSource(updatedSource).build();
        _repository.addOrUpdateReport(updatedReport, _organization);

        final Optional<Report> result = _repository.getReport(report.getId(), _organization);
        assertTrue(result.isPresent());

        final Report retrievedReport = result.get();
        assertThat(retrievedReport.getSource(), equalTo(updatedSource));
    }

    @Test
    public void testReportQuery() {
        final int reportCount = 5;
        final int testOffset = 2;
        final List<Report> reports = new ArrayList<>();
        for (int i = 1; i <= reportCount; i++) {
            final Report report =
                    TestBeanFactory.createReportBuilder()
                            .setName("test report #" + i)
                            .build();
            _repository.addOrUpdateReport(report, _organization);
            reports.add(report);
        }
        final List<Report> expectedReports = reports.subList(testOffset, reportCount);
        final Report[] expectedValues = new Report[expectedReports.size()];
        expectedReports.toArray(expectedValues);

        final QueryResult<Report> results =
                _repository.createReportQuery(_organization)
                        .limit(100)
                        .offset(testOffset)
                        .execute();

        assertThat(results.values(), hasSize(expectedValues.length));
        assertThat(results.values(), containsInAnyOrder(expectedValues));
        assertThat(results.total(), equalTo((long) reportCount));
    }

    @Test
    public void testQueryAllJobs() {
        final int reportCount = 5;
        final List<Report> reports = new ArrayList<>();
        for (int i = 1; i <= reportCount; i++) {
            final Report report =
                    TestBeanFactory.createReportBuilder()
                            .setName("test report #" + i)
                            .build();
            _repository.addOrUpdateReport(report, _organization);
            reports.add(report);
        }
        final Report[] reportsArray = new Report[reports.size()];
        reports.toArray(reportsArray);

        final JobQuery<Report.Result> query = _repository.createJobQuery(_organization);

        final QueryResult<Job<Report.Result>> results = query.execute();
        assertThat(results.values(), hasSize(reportCount));
        assertThat(results.values(), containsInAnyOrder(reportsArray));
        assertThat(results.total(), equalTo((long) reportCount));
    }

    @Test
    public void testJobQueryClauseWithOffset() {
        final int reportCount = 5;
        final int testOffset = 2;
        for (int i = 0; i < reportCount; i++) {
            final Report report = TestBeanFactory.createReportBuilder().build();
            _repository.addOrUpdateReport(report, _organization);
        }

        final JobQuery<Report.Result> baseQuery = _repository.createJobQuery(_organization);

        final QueryResult<Job<Report.Result>> results = baseQuery.offset(testOffset).execute();
        assertThat(results.values(), hasSize(reportCount - testOffset));
        assertThat(results.total(), equalTo((long) reportCount));

        final QueryResult<Job<Report.Result>> emptyResults = baseQuery.offset(reportCount).execute();
        assertThat(emptyResults.values(), empty());
        assertThat(results.total(), equalTo((long) reportCount));
    }

    @Test
    public void testJobQueryClauseWithLimit() {
        final int reportCount = 5;
        for (int i = 0; i < reportCount; i++) {
            final Report report = TestBeanFactory.createReportBuilder().build();
            _repository.addOrUpdateReport(report, _organization);
        }

        final JobQuery<Report.Result> query = _repository.createJobQuery(_organization);

        final QueryResult<Job<Report.Result>> results = query.limit(1).execute();
        assertThat(results.values(), hasSize(1));
        assertThat(results.total(), equalTo((long) reportCount));

        final QueryResult<Job<Report.Result>> allResults = query.limit(reportCount * 2).execute();
        assertThat(allResults.values(), hasSize(reportCount));
        assertThat(results.total(), equalTo((long) reportCount));
    }

    @Test
    public void testJobQueryClauseWithOffsetAndLimit() {
        final int reportCount = 10;
        final int testOffset = 3;
        final int testLimit = 2;
        for (int i = 0; i < reportCount; i++) {
            final Report report = TestBeanFactory.createReportBuilder().build();
            _repository.addOrUpdateReport(report, _organization);
        }

        final JobQuery<Report.Result> query = _repository.createJobQuery(_organization);

        final QueryResult<Job<Report.Result>> results = query.offset(testOffset).limit(testLimit).execute();
        assertThat(results.total(), equalTo((long) reportCount));

        final QueryResult<Job<Report.Result>> singleResult = query.offset(reportCount - 1).limit(testLimit).execute();
        assertThat(singleResult.values(), hasSize(1));
        assertThat(results.total(), equalTo((long) reportCount));

        final QueryResult<Job<Report.Result>> emptyResults = query.offset(reportCount).limit(reportCount).execute();
        assertThat(emptyResults.values(), empty());
        assertThat(results.total(), equalTo((long) reportCount));
    }

    @Test
    public void testJobQueryReturnsNothing() {
        final JobQuery<Report.Result> query = _repository.createJobQuery(_organization);
        final List<? extends Job<Report.Result>> results = query.execute().values();
        assertThat(results, empty());
    }

    private Optional<ReportExecution> getExecution(final UUID reportId, final Organization organization, final Instant scheduled) {
        return _server.find(ReportExecution.class)
                .where()
                .eq("report.uuid", reportId)
                .eq("report.organization.uuid", organization.getId())
                .eq("scheduled", scheduled)
                .findOneOrEmpty();
    }
}
