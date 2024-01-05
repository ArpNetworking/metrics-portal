/*
 * Copyright 2016 Groupon.com
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
package com.arpnetworking.metrics.portal;

import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.arpnetworking.kairos.client.models.Aggregator;
import com.arpnetworking.kairos.client.models.Sampling;
import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.arpnetworking.metrics.portal.reports.RecipientType;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.NeverSchedule;
import com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule;
import com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule;
import com.arpnetworking.rollups.ConsistencyChecker;
import com.arpnetworking.rollups.RollupDefinition;
import com.arpnetworking.rollups.RollupPeriod;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import models.cassandra.Host;
import models.internal.MetricsSoftwareState;
import models.internal.Organization;
import models.internal.TimeRange;
import models.internal.impl.DefaultOrganization;
import models.internal.impl.DefaultRecipient;
import models.internal.impl.DefaultRenderedReport;
import models.internal.impl.DefaultReport;
import models.internal.impl.HtmlReportFormat;
import models.internal.impl.PdfReportFormat;
import models.internal.impl.WebPageReportSource;
import models.internal.reports.ReportFormat;
import models.internal.scheduling.Period;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Builds valid beans with default content for tests.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
public final class TestBeanFactory {

    private static final String TEST_HOST = "test-host";
    private static final String TEST_CLUSTER = "test-cluster";
    private static final String TEST_METRIC = "test-metric";
    private static final String TEST_SERVICE = "test-service";
    private static final String TEST_NAME = "test-name";
    private static final String TEST_ETAG = "test-etag";
    private static final String TEST_TITLE = "test-title";
    private static final URI TEST_URI = URI.create("http://example.com");
    private static final int TEST_PERIOD_IN_SECONDS = 600;
    private static final String TEST_STATISTIC = "metrics_seen_sum";
    private static final String TEST_QUANTITY_UNIT = "test-unit";
    private static final List<String> NAGIOS_SEVERITY = Arrays.asList("CRITICAL", "WARNING", "OK");
    private static final String TEST_NAGIOS_NOTIFY = "abc@example.com";
    private static final Random RANDOM = new Random();
    private static final Organization DEFAULT_ORGANIZATION = new DefaultOrganization.Builder()
            .setId(UUID.fromString("0eb03110-2a36-4cb1-861f-7375afc98b9b"))
            .build();

    private TestBeanFactory() {
    }

    /**
     * Factory method for creating a test report builder.
     *
     * @return a report builder
     */
    public static DefaultReport.Builder createReportBuilder() {
        final ReportFormat format;
        if (RANDOM.nextInt(2) == 0) {
            format = new HtmlReportFormat.Builder().build();
        } else {
            format = new PdfReportFormat.Builder()
                    .setHeightInches((RANDOM.nextInt(100) + 1) * 11f / 100f)
                    .setWidthInches((RANDOM.nextInt(100) + 1) * 8.5f / 100f)
                    .build();
        }
        final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        final LocalDateTime runAtAndAfter = now.plusDays(RANDOM.nextInt(100) - 50);
        final LocalDateTime runUntil = runAtAndAfter.plusDays(RANDOM.nextInt(100) + 1);
        final Schedule schedule;
        switch (RANDOM.nextInt(3)) {
            case 2:
                final ChronoUnit chronoUnit = Period.values()[
                        RANDOM.nextInt(Period.values().length)].toChronoUnit();
                final ZoneId zoneId = ZoneId.of(ZoneId.getAvailableZoneIds().toArray(new String[0])[
                        RANDOM.nextInt(ZoneId.getAvailableZoneIds().size())]);
                schedule = new PeriodicSchedule.Builder()
                        .setRunAtAndAfter(runAtAndAfter.toInstant(ZoneOffset.UTC))
                        .setRunUntil(runUntil.toInstant(ZoneOffset.UTC))
                        .setPeriod(chronoUnit)
                        .setOffset(chronoUnit.getDuration().dividedBy(RANDOM.nextInt(9) + 2))
                        .setZone(zoneId)
                        .build();
                break;
            case 1:
                schedule = new OneOffSchedule.Builder()
                        .setRunAtAndAfter(runAtAndAfter.toInstant(ZoneOffset.UTC))
                        .build();
                break;
            case 0:
            default:
                schedule = NeverSchedule.getInstance();
        }
        return new DefaultReport.Builder()
                .setId(UUID.randomUUID())
                .setETag(TEST_ETAG + UUID.randomUUID().toString())
                .setName(TEST_NAME + UUID.randomUUID().toString())
                .setTimeout(Duration.ofSeconds(1 + RANDOM.nextInt(120)))
                .setRecipients(ImmutableSetMultimap.of(
                        format,
                        new DefaultRecipient.Builder()
                                .setId(UUID.randomUUID())
                                .setType(RecipientType.EMAIL)
                                .setAddress(UUID.randomUUID().toString().replace("-", "") + "@example.com")
                                .build()))
                .setReportSource(createWebPageReportSourceBuilder().build())
                .setSchedule(schedule);
    }

    /**
     * Factory method for creating a test bean report.
     *
     * @param organization The parent organization of the report.
     * @return a report
     */
    public static models.ebean.Report createEbeanReport(final models.ebean.Organization organization) {
        final models.ebean.NeverReportSchedule schedule = new models.ebean.NeverReportSchedule();
        schedule.setRunAt(Instant.EPOCH); // This can't be empty but will never run.

        final models.ebean.WebPageReportSource source = new models.ebean.WebPageReportSource();
        source.setUuid(UUID.randomUUID());
        source.setTitle(TEST_TITLE);
        source.setUri(TEST_URI);

        final models.ebean.Report ebeanReport = new models.ebean.Report();
        ebeanReport.setOrganization(organization);
        ebeanReport.setUuid(UUID.randomUUID());
        ebeanReport.setName(TEST_NAME + UUID.randomUUID().toString());
        ebeanReport.setReportSource(source);
        ebeanReport.setSchedule(schedule);
        return ebeanReport;
    }

    /**
     * Factory method for creating a report recipient.
     *
     * @return a report recipient
     */
    public static DefaultRecipient.Builder createRecipientBuilder() {
        return new DefaultRecipient.Builder()
                .setAddress(UUID.randomUUID().toString().replace("-", "") + "@example.com")
                .setType(RecipientType.EMAIL)
                .setId(UUID.randomUUID());
    }

    /**
     * Factory method for creating a {@link DefaultRenderedReport} builder.
     *
     * @return a rendered report builder
     */
    public static DefaultRenderedReport.Builder createRenderedReportBuilder() {
        return new DefaultRenderedReport.Builder()
                .setReport(createReportBuilder().build())
                .setFormat(new HtmlReportFormat.Builder().build())
                .setTimeRange(new TimeRange(Instant.now(), Instant.now().plus(Duration.ofSeconds((long) (100000 * RANDOM.nextDouble())))))
                .setGeneratedAt(Instant.now().plus(Duration.ofSeconds(RANDOM.nextInt(2))))
                .setBytes("report content".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Factory method for creating a {@link WebPageReportSource.Builder}.
     *
     * @return the builder.
     */
    public static WebPageReportSource.Builder createWebPageReportSourceBuilder() {
        return new WebPageReportSource.Builder()
                .setTitle(TEST_TITLE + UUID.randomUUID().toString())
                .setId(UUID.randomUUID())
                .setUri(URI.create("http://" + UUID.randomUUID().toString().replace("-", "") + ".example.com"))
                .setIgnoreCertificateErrors(false);
    }

    /**
     * Factory method for creating a {@link Sampling.Builder}.
     *
     * @return the builder.
     */
    public static Sampling.Builder createSamplingBuilder() {
        return new Sampling.Builder().setValue(1).setUnit(SamplingUnit.HOURS);
    }

    /**
     * Factory method for creating a {@link RollupDefinition.Builder}.
     *
     * @return the builder.
     */
    public static RollupDefinition.Builder createRollupDefinitionBuilder() {
        return new RollupDefinition.Builder()
                .setStartTime(Instant.now())
                .setSourceMetricName("my_metric")
                .setDestinationMetricName("my_metric_1h")
                .setPeriod(RollupPeriod.HOURLY)
                .setAllMetricTags(ImmutableMultimap.of("tag", "val"))
                .setFilterTags(ImmutableMap.of());
    }

    /**
     * Factory method for creating a {@link Aggregator.Builder}.
     *
     * @return the builder.
     */
    public static Aggregator.Builder createAggregatorBuilder() {
        return new Aggregator.Builder()
                .setName("count")
                .setSampling(createSamplingBuilder().build());
    }

    /**
     * Factory method for creating a {@link Aggregator.Builder}.
     *
     * @param populate populate the builder produced by {@link ThreadLocalBuilder}.
     * @return the builder.
     */
    public static ConsistencyChecker.Task buildConsistencyCheckerTaskBuilder(final Consumer<ConsistencyChecker.Task.Builder> populate) {
        return ThreadLocalBuilder.build(ConsistencyChecker.Task.Builder.class, b -> {
            b.setSourceMetricName("my_metric")
                    .setRollupMetricName("my_metric_1h")
                    .setPeriod(RollupPeriod.HOURLY)
                    .setStartTime(Instant.EPOCH)
                    .setTrigger(ConsistencyChecker.Task.Trigger.ON_DEMAND);
            populate.accept(b);
        });
    }

    /**
     * Mapping method to create a organization from an ebean organization.
     *
     * @param ebeanOrganization ebean organization
     * @return organization
     */
    public static Organization organizationFrom(final models.ebean.Organization ebeanOrganization) {
        return new DefaultOrganization.Builder()
                .setId(ebeanOrganization.getUuid())
                .build();
    }

    /**
     * Factory method to create an random organization.
     *
     * @return an organization
     */
    public static Organization newOrganization() {
        return organizationFrom(UUID.randomUUID());
    }

    /**
     * Factory method to create an organization from a uuid.
     *
     * @param id uuid for organization
     * @return an organization
     */
    public static Organization organizationFrom(final UUID id) {
        return new DefaultOrganization.Builder()
                .setId(id)
                .build();
    }

    /**
     * Retrieve the default organization.
     *
     * @return default organization
     * @deprecated tests should not use the same organization id to ensure test isolation
     */
    @Deprecated
    public static Organization getDefautOrganization() {
        return DEFAULT_ORGANIZATION;
    }

    /**
     * Factory method to create a new organization.
     *
     * @return an organization
     */
    public static Organization createOrganization() {
        return new DefaultOrganization.Builder()
                .setId(UUID.randomUUID())
                .build();
    }

    /**
     * Factory method to create a new ebean organization.
     *
     * @return an ebean organization
     */
    public static models.ebean.Organization createEbeanOrganization() {
        final models.ebean.Organization organization = new models.ebean.Organization();
        organization.setUuid(UUID.randomUUID());
        return organization;
    }

    /**
     * Factory method for creating a cassandra host.
     *
     * @param organization the {@link Organization} to create the host in
     * @return a cassandra host
     */
    public static Host createCassandraHost(final Organization organization) {
        final Host host = new Host();
        host.setName(TEST_HOST + UUID.randomUUID().toString() + ".example.com");
        host.setCluster(TEST_CLUSTER + UUID.randomUUID().toString());
        host.setMetricsSoftwareState(MetricsSoftwareState.values()[RANDOM.nextInt(MetricsSoftwareState.values().length)].name());
        host.setOrganization(organization.getId());
        return host;
    }
}
