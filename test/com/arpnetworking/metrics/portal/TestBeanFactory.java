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

import com.google.common.collect.ImmutableMap;
import io.ebean.Ebean;
import models.cassandra.Host;
import models.ebean.Expression;
import models.ebean.NagiosExtension;
import models.ebean.ReportRecipient;
import models.ebean.ReportRecipientGroup;
import models.ebean.ReportingJob;
import models.ebean.ReportingSchedule;
import models.internal.Alert;
import models.internal.Context;
import models.internal.MetricsSoftwareState;
import models.internal.Operator;
import models.internal.Organization;
import models.internal.impl.DefaultAlert;
import models.internal.impl.DefaultExpression;
import models.internal.impl.DefaultOrganization;
import models.internal.impl.DefaultQuantity;
import org.joda.time.Period;

import java.sql.Timestamp;
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
    private static final String TEST_GROUP = "test-group";
    private static final String TEST_HOST = "test-host";
    private static final String TEST_CLUSTER = "test-cluster";
    private static final String TEST_METRIC = "test-metric";
    private static final String TEST_SERVICE = "test-service";
    private static final String TEST_SCRIPT = "test-script";
    private static final List<Context> CONTEXTS = Arrays.asList(Context.CLUSTER, Context.HOST);
    private static final String TEST_NAME = "test-name";
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

    public static ReportingJob createEbeanReportingJob() {
        final ReportingSchedule schedule = new ReportingSchedule();
        schedule.setSendAt(Timestamp.from(Instant.now()));

        final ReportingJob job = new ReportingJob();
        job.setUuid(UUID.randomUUID());
        job.setSchedule(schedule);
        job.setName(TEST_NAME);
        return job;
    }

    public static ReportRecipientGroup createEbeanReportRecipientGroup() {
        final UUID groupUuid = UUID.randomUUID();
        final ReportRecipientGroup group = new ReportRecipientGroup();
        group.setUuid(groupUuid);
        group.addRecipient(ReportRecipient.newEmailRecipient(TEST_EMAIL));
        group.setName(TEST_GROUP);
        return group;
    }

    public static Organization organizationFrom(final models.ebean.Organization organization) {
        return new DefaultOrganization.Builder()
                .setId(organization.getUuid())
                .build();
    }

    public static Organization organizationFrom(final UUID id) {
        return new DefaultOrganization.Builder()
                .setId(id)
                .build();
    }

    public static models.ebean.Organization createEbeanOrganization() {
        final models.ebean.Organization organization = new models.ebean.Organization();
        organization.setUuid(UUID.randomUUID());
        return organization;
    }

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
                .setPeriod(Period.seconds(RANDOM.nextInt(100)).normalizedStandard())
                .setStatistic(TEST_STATISTIC + RANDOM.nextInt(100))
                .setValue(new DefaultQuantity.Builder()
                        .setValue(100 + RANDOM.nextDouble())
                        .setUnit(TEST_QUANTITY_UNIT + RANDOM.nextInt(100))
                        .build());
    }

    public static Alert createAlert() {
        return createAlertBuilder().build();
    }

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

    public static Map<String, String> createCassandraNagiosExtension() {
        return new ImmutableMap.Builder<String, String>()
                .put("severity", NAGIOS_SEVERITY.get(RANDOM.nextInt(NAGIOS_SEVERITY.size())))
                .put("notify", TEST_NAGIOS_NOTIFY)
                .put("attempts", Integer.toString(1 + RANDOM.nextInt(10)))
                .put("freshness", Long.toString((long) RANDOM.nextInt(1000)))
                .build();
    }

    public static models.internal.NagiosExtension createNagiosExtension() {
        return new models.internal.NagiosExtension.Builder()
                .setSeverity(NAGIOS_SEVERITY.get(RANDOM.nextInt(NAGIOS_SEVERITY.size())))
                .setNotify(TEST_NAGIOS_NOTIFY)
                .setMaxCheckAttempts(1 + RANDOM.nextInt(10))
                .setFreshnessThresholdInSeconds((long) RANDOM.nextInt(1000))
                .build();
    }

    public static NagiosExtension createEbeanNagiosExtension() {
        final NagiosExtension nagiosExtension = new NagiosExtension();
        nagiosExtension.setSeverity(NAGIOS_SEVERITY.get(RANDOM.nextInt(NAGIOS_SEVERITY.size())));
        nagiosExtension.setNotify(TEST_NAGIOS_NOTIFY);
        nagiosExtension.setMaxCheckAttempts(1 + RANDOM.nextInt(10));
        nagiosExtension.setFreshnessThreshold((long) RANDOM.nextInt(1000));
        return nagiosExtension;
    }

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

    public static DefaultExpression.Builder createExpressionBuilder() {
        return new DefaultExpression.Builder()
                .setId(UUID.randomUUID())
                .setCluster(TEST_CLUSTER + RANDOM.nextInt(100))
                .setMetric(TEST_METRIC + RANDOM.nextInt(100))
                .setScript(TEST_SCRIPT + RANDOM.nextInt(100))
                .setService(TEST_SERVICE + RANDOM.nextInt(100));
    }

    public static models.internal.Expression createExpression() {
        return createExpressionBuilder().build();
    }

    public static Host createCassandraHost() {
        final Host host = new Host();
        host.setName(TEST_HOST + RANDOM.nextInt(100) + ".example.com");
        host.setCluster(TEST_CLUSTER + RANDOM.nextInt(100));
        host.setMetricsSoftwareState(MetricsSoftwareState.values()[RANDOM.nextInt(MetricsSoftwareState.values().length)].name());
        host.setOrganization(Organization.DEFAULT.getId());
        return host;
    }

    private TestBeanFactory() {}
}
