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

import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import io.ebean.Ebean;
import models.cassandra.Host;
import models.ebean.ChromeScreenshotReportSource;
import models.ebean.Expression;
import models.ebean.HtmlReportFormat;
import models.ebean.NagiosExtension;
import models.ebean.Recipient;
import models.ebean.ReportFormat;
import models.ebean.ReportSchedule;
import models.internal.Context;
import models.internal.MetricsSoftwareState;
import models.internal.Operator;
import models.internal.Organization;
import models.internal.impl.DefaultAlert;
import models.internal.impl.DefaultExpression;
import models.internal.impl.DefaultOrganization;
import models.internal.impl.DefaultQuantity;
import models.internal.impl.DefaultReport;
import models.internal.reports.ReportSource;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Builds valid beans with default content for tests.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
public final class TestBeanFactory {

    private static final String TEST_EMAIL = "noreply+email-recipient@test.com";
    private static final String TEST_HOST = "test-host";
    private static final String TEST_CLUSTER = "test-cluster";
    private static final String TEST_METRIC = "test-metric";
    private static final String TEST_SERVICE = "test-service";
    private static final String TEST_SCRIPT = "test-script";
    private static final List<Context> CONTEXTS = Arrays.asList(Context.CLUSTER, Context.HOST);
    private static final String TEST_NAME = "test-name";
    private static final String TEST_ETAG = "test-etag";
    private static final List<Operator> OPERATORS = Arrays.asList(
            Operator.EQUAL_TO,
            Operator.GREATER_THAN,
            Operator.GREATER_THAN_OR_EQUAL_TO,
            Operator.LESS_THAN_OR_EQUAL_TO,
            Operator.LESS_THAN,
            Operator.NOT_EQUAL_TO);
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
        final ReportSource source = createEbeanReportSource().toInternal();

        final Schedule schedule = new OneOffSchedule.Builder()
                .setRunAtAndAfter(Instant.now().plus(Duration.ofHours(1)))
                .setRunUntil(null)
                .build();

        final models.internal.impl.PdfReportFormat format =
                new models.internal.impl.PdfReportFormat.Builder()
                        .setWidthInches(8.5f)
                        .setHeightInches(11.0f)
                        .build();

        final models.internal.reports.Recipient recipient = createRecipient();

        return new DefaultReport.Builder()
                .setId(UUID.randomUUID())
                .setETag(TEST_ETAG)
                .setName(TEST_NAME)
                .setRecipients(ImmutableSetMultimap.of(format, recipient))
                .setReportSource(source)
                .setSchedule(schedule);
    }

    /**
     * Factory method for creating an ebean report.
     *
     * @return an ebean report
     */
    public static models.ebean.Report createEbeanReport() {
        final ReportSchedule schedule = new ReportSchedule();
        schedule.setRunAt(Instant.now());
        schedule.setRunUntil(Instant.now().plus(Duration.ofDays(1)));

        final ReportFormat format = new HtmlReportFormat();
        final models.ebean.ReportSource source = TestBeanFactory.createEbeanReportSource();
        final Recipient recipient = TestBeanFactory.createEbeanReportRecipient();

        final models.ebean.Report report = new models.ebean.Report();
        report.setName(TEST_NAME);
        report.setOrganization(TestBeanFactory.createEbeanOrganization());
        report.setRecipients(ImmutableSetMultimap.of(format, recipient));
        report.setReportSource(source);
        report.setSchedule(schedule);
        report.setUuid(UUID.randomUUID());

        Ebean.save(report.getOrganization());
        return report;
    }

    /**
     * Factory method for creating an ebean report source.
     *
     * @return an ebean report source
     */
    public static models.ebean.ReportSource createEbeanReportSource() {
        final UUID sourceUuid = UUID.randomUUID();
        final URI testUri = URI.create("http://test-url.com");
        final models.ebean.ChromeScreenshotReportSource source = new ChromeScreenshotReportSource();
        source.setUri(testUri);
        source.setUuid(sourceUuid);
        source.setTitle("Test title");
        source.setTriggeringEventName("onload");
        return source;
    }

    /**
     * Factory method for creating a report recipient.
     *
     * @return a report recipient
     */
    public static models.internal.reports.Recipient createRecipient() {
        final UUID recipientId = UUID.randomUUID();
        return new models.internal.impl.DefaultEmailRecipient.Builder()
                .setAddress(TEST_EMAIL)
                .setId(recipientId)
                .build();

    }

    /**
     * Factory method for creating an ebean report recipient.
     *
     * @return an ebean report recipient
     */
    public static Recipient createEbeanReportRecipient() {
        final UUID recipientId = UUID.randomUUID();

        final Recipient recipient = Recipient.newEmailRecipient(TEST_EMAIL);
        recipient.setUuid(recipientId);
        return recipient;
    }

    /**
     * Mapping method to create a organization from an ebean organization.
     *
     * @param organization ebean organization
     * @return organization
     */
    public static Organization organizationFrom(final models.ebean.Organization organization) {
        return new DefaultOrganization.Builder()
                .setId(organization.getUuid())
                .build();
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
     */
    public static Organization getDefautOrganization() {
        return DEFAULT_ORGANIZATION;
    }

    /**
     * Factory method to create an ebean organization.
     *
     * @return an ebean organization
     */
    public static models.ebean.Organization createEbeanOrganization() {
        final models.ebean.Organization organization = new models.ebean.Organization();
        organization.setUuid(UUID.randomUUID());
        return organization;
    }

    /**
     * Factory method to create an alert builder.
     *
     * @return an alert builder
     */
    public static DefaultAlert.Builder createAlertBuilder() {
        return new DefaultAlert.Builder()
                .setId(UUID.randomUUID())
                .setCluster(TEST_CLUSTER + RANDOM.nextInt(100))
                .setMetric(TEST_METRIC + RANDOM.nextInt(100))
                .setContext(CONTEXTS.get(RANDOM.nextInt(CONTEXTS.size())))
                .setService(TEST_SERVICE + RANDOM.nextInt(100))
                .setNagiosExtension(createNagiosExtension())
                .setName(TEST_NAME + RANDOM.nextInt(100))
                .setOperator(OPERATORS.get(RANDOM.nextInt(OPERATORS.size())))
                .setPeriod(Duration.ofSeconds(RANDOM.nextInt(100)))
                .setStatistic(TEST_STATISTIC + RANDOM.nextInt(100))
                .setValue(new DefaultQuantity.Builder()
                        .setValue(100 + RANDOM.nextDouble())
                        .setUnit(TEST_QUANTITY_UNIT + RANDOM.nextInt(100))
                        .build());
    }

    /**
     * Factory method to create an ebean alert.
     *
     * @return an ebean alert
     */
    public static models.ebean.Alert createEbeanAlert() {
        final models.ebean.Organization organization = createEbeanOrganization();
        Ebean.save(organization);
        final models.ebean.Alert ebeanAlert = new models.ebean.Alert();
        ebeanAlert.setOrganization(organization);
        ebeanAlert.setUuid(UUID.randomUUID());
        ebeanAlert.setNagiosExtension(createEbeanNagiosExtension());
        ebeanAlert.setName(TEST_NAME + RANDOM.nextInt(100));
        ebeanAlert.setOperator(OPERATORS.get(RANDOM.nextInt(OPERATORS.size())));
        ebeanAlert.setPeriod(TEST_PERIOD_IN_SECONDS + RANDOM.nextInt(100));
        ebeanAlert.setStatistic(TEST_STATISTIC + RANDOM.nextInt(100));
        ebeanAlert.setQuantityValue(100 + RANDOM.nextDouble());
        ebeanAlert.setQuantityUnit(TEST_QUANTITY_UNIT + RANDOM.nextInt(100));
        ebeanAlert.setCluster(TEST_CLUSTER + RANDOM.nextInt(100));
        ebeanAlert.setMetric(TEST_METRIC + RANDOM.nextInt(100));
        ebeanAlert.setContext(CONTEXTS.get(RANDOM.nextInt(CONTEXTS.size())));
        ebeanAlert.setService(TEST_SERVICE + RANDOM.nextInt(100));
        return ebeanAlert;
    }

    /**
     * Factory method to crean a cassandra alert.
     *
     * @return a cassandra alert
     */
    public static models.cassandra.Alert createCassandraAlert() {
        final models.cassandra.Alert cassandraAlert = new models.cassandra.Alert();
        cassandraAlert.setOrganization(UUID.randomUUID());
        cassandraAlert.setUuid(UUID.randomUUID());
        cassandraAlert.setNagiosExtensions(createCassandraNagiosExtension());
        cassandraAlert.setName(TEST_NAME + RANDOM.nextInt(100));
        cassandraAlert.setOperator(OPERATORS.get(RANDOM.nextInt(OPERATORS.size())));
        cassandraAlert.setPeriodInSeconds(TEST_PERIOD_IN_SECONDS + RANDOM.nextInt(100));
        cassandraAlert.setStatistic(TEST_STATISTIC + RANDOM.nextInt(100));
        cassandraAlert.setQuantityValue(100 + RANDOM.nextDouble());
        cassandraAlert.setQuantityUnit(TEST_QUANTITY_UNIT + RANDOM.nextInt(100));
        cassandraAlert.setCluster(TEST_CLUSTER + RANDOM.nextInt(100));
        cassandraAlert.setMetric(TEST_METRIC + RANDOM.nextInt(100));
        cassandraAlert.setContext(CONTEXTS.get(RANDOM.nextInt(CONTEXTS.size())));
        cassandraAlert.setService(TEST_SERVICE + RANDOM.nextInt(100));
        return cassandraAlert;
    }

    /**
     * Factory method create create a cassandra nagios extension map.
     *
     * @return map of nagios extension key/value pairs
     */
    public static Map<String, String> createCassandraNagiosExtension() {
        return new ImmutableMap.Builder<String, String>()
                .put("severity", NAGIOS_SEVERITY.get(RANDOM.nextInt(NAGIOS_SEVERITY.size())))
                .put("notify", TEST_NAGIOS_NOTIFY)
                .put("attempts", Integer.toString(1 + RANDOM.nextInt(10)))
                .put("freshness", Long.toString((long) RANDOM.nextInt(1000)))
                .build();
    }

    /**
     * Factory method to create a nagios extension.
     *
     * @return a nagios extension
     */
    public static models.internal.NagiosExtension createNagiosExtension() {
        return new models.internal.NagiosExtension.Builder()
                .setSeverity(NAGIOS_SEVERITY.get(RANDOM.nextInt(NAGIOS_SEVERITY.size())))
                .setNotify(TEST_NAGIOS_NOTIFY)
                .setMaxCheckAttempts(1 + RANDOM.nextInt(10))
                .setFreshnessThresholdInSeconds((long) RANDOM.nextInt(1000))
                .build();
    }

    /**
     * Factory method to create an ebean nagios extension.
     *
     * @return an ebean nagios extension
     */
    public static NagiosExtension createEbeanNagiosExtension() {
        final NagiosExtension nagiosExtension = new NagiosExtension();
        nagiosExtension.setSeverity(NAGIOS_SEVERITY.get(RANDOM.nextInt(NAGIOS_SEVERITY.size())));
        nagiosExtension.setNotify(TEST_NAGIOS_NOTIFY);
        nagiosExtension.setMaxCheckAttempts(1 + RANDOM.nextInt(10));
        nagiosExtension.setFreshnessThreshold((long) RANDOM.nextInt(1000));
        return nagiosExtension;
    }

    /**
     * Factory method to create an ebean expression.
     *
     * @return an ebean expression
     */
    public static Expression createEbeanExpression() {
        final models.ebean.Organization organization = createEbeanOrganization();
        Ebean.save(organization);
        final models.ebean.Expression ebeanExpression = new models.ebean.Expression();
        ebeanExpression.setOrganization(organization);
        ebeanExpression.setUuid(UUID.randomUUID());
        ebeanExpression.setCluster(TEST_CLUSTER + RANDOM.nextInt(100));
        ebeanExpression.setMetric(TEST_METRIC + RANDOM.nextInt(100));
        ebeanExpression.setScript(TEST_SCRIPT + RANDOM.nextInt(100));
        ebeanExpression.setService(TEST_SERVICE + RANDOM.nextInt(100));
        return ebeanExpression;
    }

    /**
     * Factory method for creating an expression builder.
     *
     * @return an expression builder
     */
    public static DefaultExpression.Builder createExpressionBuilder() {
        return new DefaultExpression.Builder()
                .setId(UUID.randomUUID())
                .setCluster(TEST_CLUSTER + RANDOM.nextInt(100))
                .setMetric(TEST_METRIC + RANDOM.nextInt(100))
                .setScript(TEST_SCRIPT + RANDOM.nextInt(100))
                .setService(TEST_SERVICE + RANDOM.nextInt(100));
    }

    /**
     * Factory method for an expression.
     *
     * @return an expression
     */
    public static models.internal.Expression createExpression() {
        return createExpressionBuilder().build();
    }

    /**
     * Factory method for creating a cassandra host.
     *
     * @return a cassandra host
     */
    public static Host createCassandraHost() {
        final Host host = new Host();
        host.setName(TEST_HOST + RANDOM.nextInt(100) + ".example.com");
        host.setCluster(TEST_CLUSTER + RANDOM.nextInt(100));
        host.setMetricsSoftwareState(MetricsSoftwareState.values()[RANDOM.nextInt(MetricsSoftwareState.values().length)].name());
        host.setOrganization(getDefautOrganization().getId());
        return host;
    }
}
