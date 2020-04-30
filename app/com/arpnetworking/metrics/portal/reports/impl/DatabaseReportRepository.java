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

import com.arpnetworking.metrics.portal.reports.ReportQuery;
import com.arpnetworking.metrics.portal.reports.ReportRepository;
import com.arpnetworking.metrics.portal.scheduling.JobQuery;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.NeverSchedule;
import com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule;
import com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.typesafe.config.Config;
import io.ebean.EbeanServer;
import io.ebean.PagedList;
import io.ebean.Transaction;
import models.ebean.NeverReportSchedule;
import models.ebean.OneOffReportSchedule;
import models.ebean.PeriodicReportSchedule;
import models.ebean.ReportSchedule;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultJobQuery;
import models.internal.impl.DefaultQueryResult;
import models.internal.impl.GrafanaReportPanelReportSource;
import models.internal.impl.HtmlReportFormat;
import models.internal.impl.PdfReportFormat;
import models.internal.impl.WebPageReportSource;
import models.internal.reports.Recipient;
import models.internal.reports.Report;
import models.internal.reports.ReportFormat;
import models.internal.reports.ReportSource;
import models.internal.scheduling.Job;
import models.internal.scheduling.Period;
import play.Environment;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.PersistenceException;

/**
 * Implementation of {@link ReportRepository} using a SQL database.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class DatabaseReportRepository implements ReportRepository {


    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseReportRepository.class);
    private static final ReportFormat.Visitor<models.ebean.ReportFormat> INTERNAL_TO_BEAN_FORMAT_VISITOR =
            new ReportFormat.Visitor<models.ebean.ReportFormat>() {
                @Override
                public models.ebean.ReportFormat visitPdf(final PdfReportFormat internalFormat) {
                    final models.ebean.PdfReportFormat beanFormat = new models.ebean.PdfReportFormat();
                    beanFormat.setWidthInches(internalFormat.getWidthInches());
                    beanFormat.setHeightInches(internalFormat.getHeightInches());
                    return beanFormat;
                }

                @Override
                public models.ebean.ReportFormat visitHtml(final HtmlReportFormat htmlFormat) {
                    return new models.ebean.HtmlReportFormat();
                }
            };

    private AtomicBoolean _isOpen = new AtomicBoolean(false);
    private final EbeanServer _ebeanServer;

    /**
     * Public constructor.
     *
     * @param environment Play's {@code Environment} instance.
     * @param config Play's {@code Configuration} instance.
     * @param ebeanServer Play's {@code EbeanServer} for this repository.
     */
    @Inject
    public DatabaseReportRepository(
            final Environment environment,
            final Config config,
            @Named("metrics_portal") final EbeanServer ebeanServer) {
        this(ebeanServer);
    }

    /**
     * Public constructor for manual configuration. This is intended for testing.
     *
     * @param ebeanServer Play's {@code EbeanServer} for this repository.
     */
    public DatabaseReportRepository(final EbeanServer ebeanServer) {
        _ebeanServer = ebeanServer;
    }

    private models.ebean.Recipient getOrCreateEbeanRecipient(final Recipient recipient) {
        final models.ebean.Recipient ebeanRecipient = _ebeanServer.createQuery(models.ebean.Recipient.class)
                .where()
                .eq("uuid", recipient.getId())
                .findOneOrEmpty()
                .orElseGet(() -> models.ebean.Recipient.newRecipient(recipient.getType(), recipient.getAddress()));
        ebeanRecipient.setUuid(recipient.getId());
        return ebeanRecipient;
    }

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
    public Optional<Report> getReport(final UUID identifier, final Organization organization) {
        assertIsOpen();

        LOGGER.debug()
                .setMessage("Getting report")
                .addData("uuid", identifier)
                .addData("organization.uuid", organization.getId())
                .log();

        return getBeanReport(identifier, organization).map(models.ebean.Report::toInternal);
    }

    @Override
    public int deleteReport(final UUID identifier, final Organization organization) {
        assertIsOpen();

        LOGGER.debug()
                .setMessage("Deleting report")
                .addData("uuid", identifier)
                .addData("organization.uuid", organization.getId())
                .log();

        // Ebean does not generate the soft-delete update correctly when using Query#delete, so instead we update
        // the 'deleted' column ourselves
        final int deleted =
            _ebeanServer.update(models.ebean.Report.class)
                    .set("deleted", true)
                    .where()
                    .eq("uuid", identifier)
                    .eq("organization.uuid", organization.getId())
                    .eq("deleted", false)
                    .update();

        if (deleted > 0) {
            LOGGER.debug()
                    .setMessage("Deleted report")
                    .addData("uuid", identifier)
                    .addData("organization.uuid", organization.getId())
                    .log();
        }
        return deleted;
    }

    private Optional<models.ebean.Report> getBeanReport(final UUID reportId, final Organization organization) {
        return _ebeanServer.find(models.ebean.Report.class)
                .where()
                .eq("uuid", reportId)
                .eq("organization.uuid", organization.getId())
                .findOneOrEmpty();
    }

    @Override
    public void addOrUpdateReport(final Report report, final Organization organization) {
        assertIsOpen();
        final models.ebean.Report ebeanReport = internalModelToBean(report);
        final Optional<models.ebean.Organization> ebeanOrganization =
                models.ebean.Organization.findByOrganization(_ebeanServer, organization);
        if (!ebeanOrganization.isPresent()) {
            throw new IllegalArgumentException("Organization not found: " + organization);
        }
        ebeanReport.setOrganization(ebeanOrganization.get());
        LOGGER.debug()
                .setMessage("Upserting report")
                .addData("report", ebeanReport)
                .addData("organization.uuid", organization.getId())
                .log();
        try (Transaction transaction = _ebeanServer.beginTransaction()) {

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
                _ebeanServer.update(ebeanReport);
            } else {
                _ebeanServer.save(ebeanReport);
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
                _ebeanServer.find(models.ebean.ReportSource.class)
                        .select("id")
                        .where()
                        .eq("uuid", source.getUuid())
                        .findOneOrEmpty()
                        .map(models.ebean.ReportSource::getId);
        if (sourceId.isPresent()) {
            source.setId(sourceId.get());
            _ebeanServer.update(source);
        } else {
            _ebeanServer.save(source);
        }
    }

    @Override
    public JobQuery<Report.Result> createJobQuery(final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Preparing query")
                .addData("organization", organization)
                .log();
        return new DefaultJobQuery<>(this, organization);
    }

    @Override
    public QueryResult<Report> queryReports(final ReportQuery query) {
        assertIsOpen();

        LOGGER.debug()
                .setMessage("Executing query")
                .addData("query", query)
                .log();

        final PagedList<models.ebean.Report> pagedReports = createReportQuery(_ebeanServer, query);

        final ImmutableList<Report> reports =
                pagedReports
                        .getList()
                        .stream()
                        .map(models.ebean.Report::toInternal)
                        .collect(ImmutableList.toImmutableList());

        return new DefaultQueryResult<>(reports, pagedReports.getTotalCount());
    }

    private static PagedList<models.ebean.Report> createReportQuery(
            final EbeanServer ebeanServer,
            final ReportQuery query) {
        final int offset = query.getOffset().orElse(0);
        final int limit = query.getLimit();

        return ebeanServer.find(models.ebean.Report.class)
                .where()
                .eq("organization.uuid", query.getOrganization().getId())
                .setFirstRow(offset)
                .setMaxRows(limit)
                .findPagedList();
    }

    private models.ebean.Report internalModelToBean(final Report internalReport) {
        final ReportSchedule schedule = internalModelToBean(internalReport.getSchedule());
        final models.ebean.ReportSource source = internalModelToBean(internalReport.getSource());

        final models.ebean.Report beanReport = new models.ebean.Report();
        beanReport.setUuid(internalReport.getId());
        beanReport.setName(internalReport.getName());
        beanReport.setSchedule(schedule);
        beanReport.setTimeout(internalReport.getTimeout().toNanos());
        beanReport.setReportSource(source);
        beanReport.setRecipients(internalModelToBean(internalReport.getRecipientsByFormat()));
        return beanReport;
    }

    private ReportSchedule internalModelToBean(final Schedule internalSchedule) {
        if (internalSchedule instanceof PeriodicSchedule) {
            final PeriodicSchedule internalPeriodic = (PeriodicSchedule) internalSchedule;
            final PeriodicReportSchedule beanPeriodic = new PeriodicReportSchedule();
            final Period beanPeriod = Period.fromChronoUnit(internalPeriodic.getPeriod());
            beanPeriodic.setRunAt(internalPeriodic.getRunAtAndAfter());
            beanPeriodic.setRunUntil(internalPeriodic.getRunUntil().orElse(null));
            beanPeriodic.setOffsetNanos(internalPeriodic.getOffset().toNanos());
            beanPeriodic.setPeriod(beanPeriod);
            beanPeriodic.setZone(internalPeriodic.getZone());
            return beanPeriodic;
        } else if (internalSchedule instanceof OneOffSchedule) {
            final OneOffSchedule internalOneOff = (OneOffSchedule) internalSchedule;
            final ReportSchedule beanOneOff = new OneOffReportSchedule();
            beanOneOff.setRunAt(internalOneOff.getRunAtAndAfter());
            beanOneOff.setRunUntil(internalOneOff.getRunUntil().orElse(null));
            return beanOneOff;
        } else if (internalSchedule instanceof NeverSchedule) {
            final NeverSchedule internalNever = (NeverSchedule) internalSchedule;
            final ReportSchedule beanNever = new NeverReportSchedule();
            beanNever.setRunAt(Instant.ofEpochSecond(0));
            beanNever.setRunUntil(internalNever.getRunUntil().orElse(null));
            return beanNever;
        }
        throw new IllegalArgumentException("Unsupported internal model: " + internalSchedule.getClass());
    }

    private models.ebean.ReportSource internalModelToBean(final ReportSource reportSource) {
        return reportSource.accept(FromInternalSourceVisitor.getInstance());
    }

    private ImmutableSetMultimap<models.ebean.ReportFormat, models.ebean.Recipient> internalModelToBean(
            final Map<ReportFormat, Collection<Recipient>> recipients
    ) {
        final ImmutableSetMultimap.Builder<models.ebean.ReportFormat, models.ebean.Recipient> multimapBuilder =
                ImmutableSetMultimap.builder();

        for (final Map.Entry<ReportFormat, Collection<Recipient>> entry : recipients.entrySet()) {
            final models.ebean.ReportFormat beanFormat = entry.getKey().accept(INTERNAL_TO_BEAN_FORMAT_VISITOR);
            for (final Recipient recipient : entry.getValue()) {
                multimapBuilder.put(beanFormat, getOrCreateEbeanRecipient(recipient));
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

    private static final class FromInternalSourceVisitor extends ReportSource.Visitor<models.ebean.ReportSource> {
        private static final FromInternalSourceVisitor INSTANCE = new FromInternalSourceVisitor();

        public static FromInternalSourceVisitor getInstance() {
            return INSTANCE;
        }

        @Override
        public models.ebean.ReportSource visitWeb(final WebPageReportSource source) {
            final models.ebean.WebPageReportSource ebeanSource = new models.ebean.WebPageReportSource();
            ebeanSource.setUuid(source.getId());
            ebeanSource.setIgnoreCertificateErrors(source.ignoresCertificateErrors());
            ebeanSource.setUri(source.getUri());
            ebeanSource.setTitle(source.getTitle());
            return ebeanSource;
        }

        @Override
        public models.ebean.ReportSource visitGrafana(final GrafanaReportPanelReportSource source) {
            final models.ebean.GrafanaReportPanelReportSource ebeanSource = new models.ebean.GrafanaReportPanelReportSource();
            ebeanSource.setUuid(source.getWebPageReportSource().getId());
            ebeanSource.setIgnoreCertificateErrors(source.getWebPageReportSource().ignoresCertificateErrors());
            ebeanSource.setUri(source.getWebPageReportSource().getUri());
            ebeanSource.setTitle(source.getWebPageReportSource().getTitle());
            return ebeanSource;
        }
    }

}
