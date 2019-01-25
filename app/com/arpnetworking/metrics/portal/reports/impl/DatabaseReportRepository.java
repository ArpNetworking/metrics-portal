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
import com.google.common.collect.ImmutableSetMultimap;
import io.ebean.DuplicateKeyException;
import io.ebean.Ebean;
import io.ebean.Transaction;
import models.ebean.PeriodicReportSchedule;
import models.ebean.ReportExecution;
import models.ebean.ReportSchedule;
import models.internal.Organization;
import models.internal.impl.ChromeScreenshotReportSource;
import models.internal.impl.DefaultEmailRecipient;
import models.internal.impl.HtmlReportFormat;
import models.internal.impl.PdfReportFormat;
import models.internal.reports.Recipient;
import models.internal.reports.Report;
import models.internal.reports.ReportFormat;
import models.internal.reports.ReportSource;
import models.internal.scheduling.Job;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.annotation.Nullable;
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

    private static final ReportFormat.Visitor<models.ebean.ReportFormat> INTERNAL_TO_BEAN_FORMAT_VISITOR =
            new ReportFormat.Visitor<models.ebean.ReportFormat>() {
                @Override
                public models.ebean.ReportFormat visit(final PdfReportFormat internalFormat) {
                    final models.ebean.PdfReportFormat beanFormat = new models.ebean.PdfReportFormat();
                    beanFormat.setWidthInches(internalFormat.getWidthInches());
                    beanFormat.setHeightInches(internalFormat.getHeightInches());
                    return beanFormat;
                }

                @Override
                public models.ebean.ReportFormat visit(final HtmlReportFormat htmlFormat) {
                    return new models.ebean.HtmlReportFormat();
                }
            };

    private static final Recipient.Visitor<models.ebean.Recipient> INTERNAL_TO_BEAN_RECIPIENT_VISITOR =
            new Recipient.Visitor<models.ebean.Recipient>() {
                @Override
                public models.ebean.Recipient visit(final DefaultEmailRecipient recipient) {
                    final models.ebean.Recipient ebeanRecipient =
                            models.ebean.Recipient
                                    .findByRecipient(recipient)
                                    .orElseGet(() -> models.ebean.Recipient.newEmailRecipient(recipient.getAddress()));
                    ebeanRecipient.setUuid(recipient.getId());
                    return ebeanRecipient;
                }
            };

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
        assertIsOpen();
        return Ebean.find(ReportExecution.class)
                .orderBy()
                .desc("completed_at")
                .where()
                .eq("report.uuid", reportId)
                .eq("report.organization.uuid", organization.getId())
                .in("state", ReportExecution.State.SUCCESS, ReportExecution.State.FAILURE)
                .findOneOrEmpty()
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

        return getBeanReport(identifier, organization).map(models.ebean.Report::toInternal);
    }

    private Optional<models.ebean.Report> getBeanReport(final UUID reportId, final Organization organization) {
        return Ebean.find(models.ebean.Report.class)
                .where()
                .eq("uuid", reportId)
                .eq("organization.uuid", organization.getId())
                .findOneOrEmpty();
    }

    @Override
    public void addOrUpdateReport(final Report report, final Organization organization) {
        assertIsOpen();
        final models.ebean.Report ebeanReport = internalModelToBean(report);
        final models.ebean.Organization ebeanOrg = models.ebean.Organization.findByOrganization(organization);
        if (ebeanOrg == null) {
            throw new EntityNotFoundException("Organization not found: " + organization.getId());
        }
        ebeanReport.setOrganization(ebeanOrg);
        LOGGER.debug()
                .setMessage("Upserting report")
                .addData("report", ebeanReport)
                .addData("organization.uuid", organization.getId())
                .log();
        try (Transaction transaction = Ebean.beginTransaction()) {

            addOrUpdateReportSource(ebeanReport.getReportSource());

            final Optional<models.ebean.Report> existingReport = getBeanReport(report.getId(), organization);
            final boolean created = !existingReport.isPresent();

            LOGGER.debug()
                    .setMessage("Attempting save report")
                    .addData("reportSource.id", ebeanReport.getReportSource().getId())
                    .addData("reportSource.uuid", ebeanReport.getReportSource().getUuid())
                    .addData("created", created)
                    .log();

            if (existingReport.isPresent()) {
                ebeanReport.setId(existingReport.get().getId());
                Ebean.update(ebeanReport);
            } else {
                Ebean.save(ebeanReport);
            }

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
                    .addData("organization.uuid", organization.getId())
                    .setThrowable(e)
                    .log();
            throw new PersistenceException(e);
        }
    }

    private void addOrUpdateReportSource(final models.ebean.ReportSource source) {
        final Optional<Long> sourceId =
                Ebean.find(models.ebean.ReportSource.class)
                        .select("id")
                        .where()
                        .eq("uuid", source.getUuid())
                        .findOneOrEmpty()
                        .map(models.ebean.ReportSource::getId);
        if (sourceId.isPresent()) {
            source.setId(sourceId.get());
            Ebean.update(source);
        } else {
            Ebean.save(source);
        }
    }

    @Override
    public void jobFailed(final UUID reportId, final Organization organization, final Instant scheduled, final Throwable error) {
        assertIsOpen();
        updateExecutionState(
                reportId,
                organization,
                scheduled,
                ReportExecution.State.FAILURE,
                null,
                error
        );
    }

    @Override
    public void jobStarted(final UUID reportId, final Organization organization, final Instant scheduled) {
        assertIsOpen();
        updateExecutionState(
                reportId,
                organization,
                scheduled,
                ReportExecution.State.STARTED,
                null,
                null
        );
    }

    @Override
    public void jobSucceeded(final UUID reportId, final Organization organization, final Instant scheduled, final Report.Result result) {
        assertIsOpen();
        updateExecutionState(
                reportId,
                organization,
                scheduled,
                ReportExecution.State.SUCCESS,
                result,
                null
        );
    }

    private void updateExecutionState(
            final UUID reportId,
            final Organization organization,
            final Instant scheduled,
            final ReportExecution.State state,
            @Nullable final Report.Result result,
            @Nullable final Throwable error
    ) {
        LOGGER.debug()
                .setMessage("Updating report executions")
                .addData("report.uuid", reportId)
                .addData("scheduled", scheduled)
                .addData("state", state)
                .log();
        try (Transaction transaction = Ebean.beginTransaction()) {
            final Optional<models.ebean.Report> report = getBeanReport(reportId, organization);
            if (!report.isPresent()) {
                final String message = String.format(
                        "Could not find report with uuid=%s, organization.uuid=%s",
                        reportId.toString(),
                        organization.getId()
                );
                throw new EntityNotFoundException(message);
            }

            final ReportExecution execution = new ReportExecution();
            execution.setError(error);
            execution.setReport(report.get());
            execution.setResult(result);
            execution.setScheduled(scheduled);
            execution.setState(state);

            switch (state) {
                case STARTED:
                    execution.setStartedAt(Instant.now());
                    execution.setCompletedAt(null);
                    break;
                case FAILURE:
                case SUCCESS:
                    execution.setCompletedAt(Instant.now());
                    break;
                default:
                    throw new AssertionError("unreachable branch");
            }

            try {
                Ebean.save(execution);
            } catch (final DuplicateKeyException ignore) {
                Ebean.update(execution);
            }

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
            throw new PersistenceException("Failed to update report executions", e);
        }
    }

    /* package */ Optional<ReportExecution> getExecution(final UUID reportId, final Organization organization, final Instant scheduled) {
        assertIsOpen();
        return Ebean.find(ReportExecution.class)
                .where()
                .eq("report.uuid", reportId)
                .eq("report.organization.uuid", organization.getId())
                .eq("scheduled", scheduled)
                .findOneOrEmpty();
    }

    private models.ebean.Report internalModelToBean(final Report internalReport) {
        final ReportSchedule schedule = internalModelToBean(internalReport.getSchedule());
        final models.ebean.ReportSource source = internalModelToBean(internalReport.getSource());

        final models.ebean.Report beanReport = new models.ebean.Report();
        beanReport.setUuid(internalReport.getId());
        beanReport.setName(internalReport.getName());
        beanReport.setSchedule(schedule);
        beanReport.setReportSource(source);
        beanReport.setRecipients(internalModelToBean(internalReport.getRecipientsByFormat()));
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
        throw new IllegalArgumentException("Unsupported internal model: " + internalSchedule.getClass());
    }

    private models.ebean.ReportSource internalModelToBean(final ReportSource reportSource) {
        if (reportSource instanceof ChromeScreenshotReportSource) {
            final ChromeScreenshotReportSource internalChromeSource = (ChromeScreenshotReportSource) reportSource;

            final models.ebean.ChromeScreenshotReportSource ebeanSource = new models.ebean.ChromeScreenshotReportSource();
            ebeanSource.setUuid(reportSource.getId());
            ebeanSource.setIgnoreCertificateErrors(internalChromeSource.ignoresCertificateErrors());
            ebeanSource.setUri(internalChromeSource.getUri());
            ebeanSource.setTriggeringEventName(internalChromeSource.getTriggeringEventName());
            ebeanSource.setTitle(internalChromeSource.getTitle());
            return ebeanSource;
        }
        throw new IllegalArgumentException("Unsupported internal model: " + reportSource.getClass());
    }

    private ImmutableSetMultimap<models.ebean.ReportFormat, models.ebean.Recipient> internalModelToBean(
            final Map<ReportFormat, Collection<Recipient>> recipients
    ) {
        final ImmutableSetMultimap.Builder<models.ebean.ReportFormat, models.ebean.Recipient> multimapBuilder =
                ImmutableSetMultimap.builder();

        for (final Map.Entry<ReportFormat, Collection<Recipient>> entry : recipients.entrySet()) {
            final models.ebean.ReportFormat beanFormat = INTERNAL_TO_BEAN_FORMAT_VISITOR.visit(entry.getKey());
            for (final Recipient recipient : entry.getValue()) {
                multimapBuilder.put(beanFormat, INTERNAL_TO_BEAN_RECIPIENT_VISITOR.visit(recipient));
            }
        }
        return multimapBuilder.build();
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
