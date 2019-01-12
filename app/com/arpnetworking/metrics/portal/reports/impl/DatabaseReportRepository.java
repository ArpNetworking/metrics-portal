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
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule;
import com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import io.ebean.Ebean;
import io.ebean.Transaction;
import models.ebean.PeriodicReportSchedule;
import models.ebean.ReportExecution;
import models.ebean.ReportRecipient;
import models.ebean.ReportRecipientGroup;
import models.ebean.ReportSchedule;
import models.internal.Organization;
import models.internal.impl.ChromeScreenshotReportSource;
import models.internal.impl.HTMLReportFormat;
import models.internal.impl.PDFReportFormat;
import models.internal.reports.RecipientGroup;
import models.internal.reports.Report;
import models.internal.reports.ReportFormat;
import models.internal.reports.ReportSource;
import models.internal.scheduling.Job;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.persistence.EntityNotFoundException;
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
    public Optional<Job<Report.Result>> getJob(final UUID id, final Organization organization) {
        return getReport(id, organization).map(Function.identity());
    }

    @Override
    public Optional<Instant> getLastRun(final UUID reportId, final Organization organization) throws NoSuchElementException {
        return getReport(reportId, organization)
                .flatMap(r ->
                        Ebean.find(ReportExecution.class)
                                .orderBy()
                                .desc("completed_at")
                                .where()
                                .eq("report_id", r.getId())
                                .eq("state", "SUCCESS")
                                .findOneOrEmpty()
                )
                .map(ReportExecution::getCompletedAt);
    }

    @Override
    public Optional<Report> getReport(final UUID identifier, final Organization organization) {
        assertIsOpen();

        LOGGER.debug()
                .setMessage("Getting report")
                .addData("uuid", identifier)
                .addData("organization.uuid", organization.getId())
                .log();

        return Optional.ofNullable(models.ebean.Organization.findByOrganization(organization))
                .flatMap(beanOrg ->
                        Ebean.find(models.ebean.Report.class)
                                .where()
                                .eq("uuid", identifier)
                                .eq("organization_id", beanOrg.getId())
                                .eq("disabled", false)
                                .findOneOrEmpty()
                                .map(models.ebean.Report::toInternal)
                );
    }

    @Override
    public void addOrUpdateReport(final Report report, final Organization organization) {
        final models.ebean.Report ebeanReport = internalModelToBean(report);
        final models.ebean.Organization ebeanOrg = models.ebean.Organization.findByOrganization(organization);
        if (ebeanOrg == null) {
            throw new EntityNotFoundException("Organization not found: " + organization.getId());
        }
        ebeanReport.setOrganization(ebeanOrg);
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Upserting report")
                .addData("report", ebeanReport)
                .addData("organization.uuid", organization.getId())
                .log();
        try (Transaction transaction = Ebean.beginTransaction()) {
            Ebean.save(ebeanReport.getReportSource());
            final Optional<models.ebean.Report> existingReport =
                    Ebean.find(models.ebean.Report.class)
                            .where()
                            .eq("uuid", ebeanReport.getUuid())
                            .eq("organization.uuid", organization.getId())
                            .findOneOrEmpty();
            final boolean created = !existingReport.isPresent();
            Ebean.save(ebeanReport);
            transaction.commit();
            LOGGER.debug()
                    .setMessage("Upserted report")
                    .addData("report", ebeanReport)
                    .addData("created", created)
                    .log();
            // CHECKSTYLE.OFF: IllegalCatchCheck
        } catch (final RuntimeException e) {
            // CHECKSTYLE.ON: IllegalCatchCheck
            LOGGER.error()
                    .setMessage("Failed to upsert report")
                    .addData("report", ebeanReport)
                    .setThrowable(e)
                    .log();
            throw new PersistenceException(e);
        }
    }

    @Override
    public void jobFailed(final UUID reportId, final Organization organization, final Instant scheduled, final Throwable error) {
        assertIsOpen();
        updateExecutionState(reportId, organization, scheduled, ReportExecution.State.FAILURE);
    }

    @Override
    public void jobStarted(final UUID reportId, final Organization organization, final Instant scheduled) {
        assertIsOpen();
        updateExecutionState(reportId, organization, scheduled, ReportExecution.State.STARTED);
    }

    @Override
    public void jobSucceeded(final UUID reportId, final Organization organization, final Instant scheduled, final Report.Result result) {
        assertIsOpen();
        updateExecutionState(reportId, organization, scheduled, ReportExecution.State.SUCCESS);
    }

    private void updateExecutionState(final UUID reportId, final Organization organization, final Instant scheduled, final ReportExecution.State state) {
        LOGGER.debug()
                .setMessage("Updating report executions")
                .addData("report.uuid", reportId)
                .addData("scheduled", scheduled)
                .addData("state", state)
                .log();
        try (Transaction transaction = Ebean.beginTransaction()) {
            final Optional<models.ebean.Report> report = Ebean.find(models.ebean.Report.class)
                    .where()
                    .eq("uuid", reportId)
                    .eq("organization_id", organization.getId())
                    .findOneOrEmpty();
            if (!report.isPresent()) {
                throw new EntityNotFoundException();
            }

            final ReportExecution execution = new ReportExecution();
            execution.setReport(report.get());
            execution.setScheduledFor(scheduled);
            execution.setState(state);

            switch (state) {
                case SUCCESS:
                    execution.setCompletedAt(Instant.now());
                    break;
                case FAILURE:
                    execution.setCompletedAt(Instant.now());
                    break;
                case STARTED:
                    execution.setStartedAt(Instant.now());
                    break;
                default:
            }

            Ebean.save(execution);
            LOGGER.debug()
                    .setMessage("Updated report execution")
                    .addData("report.uuid", reportId)
                    .addData("scheduled", scheduled)
                    .addData("state", state)
                    .log();
            transaction.commit();
            // CHECKSTYLE.OFF: IllegalCatchCheck
        } catch (final RuntimeException e) {
            // CHECKSTYLE.ON: IllegalCatchCheck
            LOGGER.error()
                    .setMessage("Failed to update report executions")
                    .addData("report.uuid", reportId)
                    .addData("scheduled", scheduled)
                    .addData("state", state)
                    .setThrowable(e)
                    .log();
            throw new PersistenceException(e);
        }
    }

    /* package */ Optional<ReportExecution> getExecution(final UUID reportId, final Organization organization, final Instant scheduled) {
        return getReport(reportId, organization).flatMap(r ->
                Ebean.find(ReportExecution.class)
                        .where()
                        .eq("report_id", r.getId())
                        .eq("scheduled_for", scheduled)
                        .findOneOrEmpty()
        );
    }

    private models.ebean.Report internalModelToBean(final Report internalReport) {
        final ReportSchedule schedule = internalModelToBean(internalReport.getSchedule());
        final models.ebean.ReportSource source = internalModelToBean(internalReport.getSource());

        final Set<ReportRecipientGroup> ebeanGroups =
                internalReport.getRecipientGroups()
                        .stream()
                        .map(this::internalModelToBean)
                        .collect(Collectors.toSet());

        final models.ebean.Report beanReport = new models.ebean.Report();
        beanReport.setUuid(internalReport.getId());
        beanReport.setSchedule(schedule);
        beanReport.setReportSource(source);
        beanReport.setRecipientGroups(ebeanGroups);
        beanReport.setName(internalReport.getName());
        return beanReport;
    }

    private ReportSchedule internalModelToBean(final Schedule internalSchedule) {
        if (internalSchedule instanceof PeriodicSchedule) {
            final PeriodicSchedule internalPeriodic = (PeriodicSchedule) internalSchedule;
            final PeriodicReportSchedule beanPeriodic = new PeriodicReportSchedule();
            final PeriodicReportSchedule.Period beanPeriod = PeriodicReportSchedule.Period.fromChronoUnit(internalPeriodic.getPeriod());
            beanPeriodic.setRunAt(internalPeriodic.getRunAtAndAfter());
            beanPeriodic.setRunUntil(internalPeriodic.getRunUntil().orElse(null));
            beanPeriodic.setOffset(internalPeriodic.getOffset());
            beanPeriodic.setPeriod(beanPeriod);
            beanPeriodic.setZone(internalPeriodic.getZone());
            return beanPeriodic;
        } else if (internalSchedule instanceof OneOffSchedule) {
            final OneOffSchedule internalOneOff = (OneOffSchedule) internalSchedule;
            final ReportSchedule beanOneOff = new ReportSchedule();
            beanOneOff.setRunAt(internalOneOff.getRunAtAndAfter());
            beanOneOff.setRunUntil(internalOneOff.getRunUntil().orElse(null));
            return beanOneOff;
        }
        throw new IllegalArgumentException("Unsupported internal model: " + internalSchedule.getClass().getSimpleName());
    }

    private models.ebean.ReportSource internalModelToBean(final ReportSource reportSource) {
        if (reportSource instanceof ChromeScreenshotReportSource) {
            final ChromeScreenshotReportSource internalChromeSource = (ChromeScreenshotReportSource) reportSource;

            final models.ebean.ChromeScreenshotReportSource ebeanSource = new models.ebean.ChromeScreenshotReportSource();
            ebeanSource.setUuid(reportSource.getId());
            ebeanSource.setIgnoreCertificateErrors(internalChromeSource.ignoresCertificateErrors());
            ebeanSource.setUrl(internalChromeSource.getUrl().toString());
            ebeanSource.setTriggeringEventName(internalChromeSource.getTriggeringEventName());
            ebeanSource.setTitle(internalChromeSource.getTitle());
            return ebeanSource;
        }
        throw new IllegalArgumentException("Unsupported internal model: " + reportSource.getClass().getSimpleName());
    }

    private ReportRecipientGroup internalModelToBean(final RecipientGroup group) {
        final ReportFormat.Visitor<models.ebean.ReportFormat> internalToBeanVisitor =
                new ReportFormat.Visitor<models.ebean.ReportFormat>() {
                    @Override
                    public models.ebean.ReportFormat visit(final PDFReportFormat internalFormat) {
                        final models.ebean.PDFReportFormat beanFormat = new models.ebean.PDFReportFormat();
                        beanFormat.setWidthInches(internalFormat.getWidth());
                        beanFormat.setHeightInches(internalFormat.getHeight());
                        return beanFormat;
                    }

                    @Override
                    public models.ebean.ReportFormat visit(final HTMLReportFormat htmlFormat) {
                        return new models.ebean.ReportFormat();
                    }
                };

        final Set<models.ebean.ReportFormat> ebeanFormats =
                group.getFormats()
                        .stream()
                        .map(f -> f.accept(internalToBeanVisitor))
                        .collect(Collectors.toSet());

        final List<ReportRecipient> recipients =
                group.getMembers()
                        .stream()
                        .map(ReportRecipient::newEmailRecipient)
                        .collect(Collectors.toList());

        final ReportRecipientGroup ebeanGroup = new ReportRecipientGroup();
        ebeanGroup.setName(group.getName());
        ebeanGroup.setFormats(ebeanFormats);
        ebeanGroup.setUuid(group.getId());
        ebeanGroup.setRecipients(recipients);
        return ebeanGroup;
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
