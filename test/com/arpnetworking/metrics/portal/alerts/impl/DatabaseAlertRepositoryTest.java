/**
 * Copyright 2015 Groupon.com
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
package com.arpnetworking.metrics.portal.alerts.impl;

import com.arpnetworking.metrics.portal.H2ConnectionStringFactory;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.ebean.Ebean;
import io.ebean.Transaction;
import models.internal.Alert;
import models.internal.AlertQuery;
import models.internal.Context;
import models.internal.NagiosExtension;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultAlertQuery;
import org.joda.time.Period;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.WithApplication;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import javax.persistence.PersistenceException;

/**
 * Tests class <code>DatabaseAlertRepository</code>.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
public class DatabaseAlertRepositoryTest extends WithApplication {

    @Before
    public void setup() {
        alertRepo.open();
    }

    @After
    public void teardown() {
        alertRepo.close();
    }

    @Test
    public void testGetForInvalidId() {
        Assert.assertFalse(alertRepo.get(UUID.randomUUID(), Organization.DEFAULT).isPresent());
    }

    @Override
    protected Application provideApplication() {
        return new GuiceApplicationBuilder()
                .configure(H2ConnectionStringFactory.generateConfiguration())
                .build();
    }

    @Test
    public void testGetForValidId() throws IOException {
        final UUID uuid = UUID.randomUUID();
        final models.ebean.Alert ebeanAlert = TestBeanFactory.createEbeanAlert();
        final Organization org = TestBeanFactory.organizationFrom(ebeanAlert.getOrganization());
        Assert.assertFalse(alertRepo.get(uuid, org).isPresent());
        ebeanAlert.setUuid(uuid);
        try (Transaction transaction = Ebean.beginTransaction()) {
            Ebean.save(ebeanAlert);
            transaction.commit();
        }
        final Optional<Alert> expected = alertRepo.get(uuid, org);
        Assert.assertTrue(expected.isPresent());
        assertAlertEbeanEquivalent(expected.get(), ebeanAlert);
    }

    @Test
    public void testGetAlertCountWithNoAlert() {
        Assert.assertEquals(0, alertRepo.getAlertCount(Organization.DEFAULT));
    }

    @Test
    public void testGetAlertCountWithMultipleAlert() throws JsonProcessingException {
        Assert.assertEquals(0, alertRepo.getAlertCount(Organization.DEFAULT));
        final Transaction transaction = Ebean.beginTransaction();
        final Organization org;
        try {
            final models.ebean.Alert ebeanAlert1 = TestBeanFactory.createEbeanAlert();
            ebeanAlert1.setUuid(UUID.randomUUID());
            Ebean.save(ebeanAlert1);
            final models.ebean.Alert ebeanAlert2 = TestBeanFactory.createEbeanAlert();
            ebeanAlert2.setUuid(UUID.randomUUID());
            ebeanAlert2.setOrganization(ebeanAlert1.getOrganization());
            Ebean.save(ebeanAlert2);
            org = TestBeanFactory.organizationFrom(ebeanAlert1.getOrganization());
            transaction.commit();
        } finally {
            transaction.end();
        }
        Assert.assertEquals(2, alertRepo.getAlertCount(org));
    }

    @Test
    public void testAddOrUpdateAlertAddCase() {
        final UUID uuid = UUID.randomUUID();
        Assert.assertFalse(alertRepo.get(uuid, Organization.DEFAULT).isPresent());
        final Alert actualAlert = TestBeanFactory.createAlertBuilder().setId(uuid).build();
        alertRepo.addOrUpdateAlert(actualAlert, Organization.DEFAULT);
        final Optional<Alert> expected = alertRepo.get(uuid, Organization.DEFAULT);
        Assert.assertTrue(expected.isPresent());
        Assert.assertEquals(expected.get(), actualAlert);
    }

    @Test
    public void testAddAlertWithNoExtension() {
        final UUID uuid = UUID.randomUUID();
        final Alert alert = TestBeanFactory.createAlertBuilder()
                .setId(uuid)
                .setNagiosExtension(null)
                .build();
        alertRepo.addOrUpdateAlert(alert, Organization.DEFAULT);
        final Alert expectedAlert = alertRepo.get(uuid, Organization.DEFAULT).get();
        Assert.assertNull(expectedAlert.getNagiosExtension());
    }

    @Test(expected = PersistenceException.class)
    public void testThrowsExceptionWhenQueryFails() throws IOException {
        final UUID uuid = UUID.randomUUID();
        alertRepo.addOrUpdateAlert(TestBeanFactory.createAlertBuilder()
                .setId(uuid)
                .setCluster("new-cluster")
                .build(), Organization.DEFAULT);
        models.ebean.Alert ebeanAlert1 = Ebean.find(models.ebean.Alert.class)
                .where()
                .eq("uuid", uuid)
                .findUnique();
        models.ebean.Alert ebeanAlert2 = Ebean.find(models.ebean.Alert.class)
                .where()
                .eq("uuid", uuid)
                .findUnique();
        try (Transaction transaction = Ebean.beginTransaction()) {
            ebeanAlert1.setCluster("new-cluster1");
            ebeanAlert2.setCluster("new-cluster2");
            Ebean.save(ebeanAlert2);
            Ebean.save(ebeanAlert1);
            transaction.commit();
        }
    }

    @Test
    public void testQueryClauseWithClusterOnly() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .setCluster("my-test-cluster")
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .build();
        alertRepo.addOrUpdateAlert(alert1, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert2, Organization.DEFAULT);
        final AlertQuery successQuery = new DefaultAlertQuery(alertRepo, Organization.DEFAULT);
        successQuery.cluster(Optional.of("my-test-cluster"));
        final QueryResult<Alert> successResult = alertRepo.query(successQuery);
        Assert.assertEquals(1, successResult.total());
        final AlertQuery failQuery = new DefaultAlertQuery(alertRepo, Organization.DEFAULT);
        failQuery.cluster(Optional.of("some-random-cluster"));
        final QueryResult<Alert> failResult = alertRepo.query(failQuery);
        Assert.assertEquals(0, failResult.total());
    }

    @Test
    public void testQueryClauseWithContextOnly() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .setContext(Context.CLUSTER)
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .setContext(Context.HOST)
                .build();
        alertRepo.addOrUpdateAlert(alert1, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert2, Organization.DEFAULT);
        final AlertQuery successQuery = new DefaultAlertQuery(alertRepo, Organization.DEFAULT);
        successQuery.context(Optional.of(Context.CLUSTER));
        final QueryResult<Alert> successResult = alertRepo.query(successQuery);
        Assert.assertEquals(1, successResult.total());
    }

    @Test
    public void testQueryClauseWithServiceOnly() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .setService("my-test-service")
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .build();
        alertRepo.addOrUpdateAlert(alert1, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert2, Organization.DEFAULT);
        final AlertQuery successQuery = new DefaultAlertQuery(alertRepo, Organization.DEFAULT);
        successQuery.service(Optional.of("my-test-service"));
        final QueryResult<Alert> successResult = alertRepo.query(successQuery);
        Assert.assertEquals(1, successResult.total());
        final AlertQuery failQuery = new DefaultAlertQuery(alertRepo, Organization.DEFAULT);
        failQuery.service(Optional.of("some-random-service"));
        final QueryResult<Alert> failResult = alertRepo.query(failQuery);
        Assert.assertEquals(0, failResult.total());
    }

    @Test
    public void testQueryClauseWithContainsOnly() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .setService("my-contained-service")
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .setCluster("my-cluster")
                .setId(UUID.randomUUID())
                .build();
        final Alert alert3 = TestBeanFactory.createAlertBuilder()
                .setName("my-contained-name")
                .setId(UUID.randomUUID())
                .build();
        final Alert alert4 = TestBeanFactory.createAlertBuilder()
                .setMetric("my-contained-metric")
                .setId(UUID.randomUUID())
                .build();
        final Alert alert5 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .build();
        alertRepo.addOrUpdateAlert(alert1, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert2, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert3, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert4, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert5, Organization.DEFAULT);
        final AlertQuery successQuery = new DefaultAlertQuery(alertRepo, Organization.DEFAULT);
        successQuery.contains(Optional.of("contained"));
        final QueryResult<Alert> successResult = alertRepo.query(successQuery);
        Assert.assertEquals(3, successResult.total());
    }

    @Test
    public void testQueryClauseWithLimit() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .setService("my-test-service")
                .setCluster("my-test-cluster")
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .setService("my-test-service")
                .setCluster("my-test-cluster")
                .build();
        alertRepo.addOrUpdateAlert(alert1, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert2, Organization.DEFAULT);
        final AlertQuery query1 = new DefaultAlertQuery(alertRepo, Organization.DEFAULT);
        query1.service(Optional.of("my-test-service"));
        query1.cluster(Optional.of("my-test-cluster"));
        query1.limit(1);
        final QueryResult<Alert> result1 = alertRepo.query(query1);
        Assert.assertEquals(1, result1.values().size());
        final AlertQuery query2 = new DefaultAlertQuery(alertRepo, Organization.DEFAULT);
        query2.service(Optional.of("my-test-service"));
        query2.cluster(Optional.of("my-test-cluster"));
        query2.limit(2);
        final QueryResult<Alert> result2 = alertRepo.query(query2);
        Assert.assertEquals(2, result2.values().size());
    }

    @Test
    public void testQueryClauseWithOffsetAndLimit() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .setService("my-test-service")
                .setCluster("my-test-cluster")
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .setService("my-test-service")
                .setCluster("my-test-cluster")
                .build();
        final Alert alert3 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .setService("my-test-service")
                .setCluster("my-test-cluster")
                .build();
        alertRepo.addOrUpdateAlert(alert1, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert2, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert3, Organization.DEFAULT);
        final AlertQuery query = new DefaultAlertQuery(alertRepo, Organization.DEFAULT);
        query.service(Optional.of("my-test-service"));
        query.cluster(Optional.of("my-test-cluster"));
        query.offset(Optional.of(2));
        query.limit(2);
        final QueryResult<Alert> result = alertRepo.query(query);
        Assert.assertEquals(1, result.values().size());
        Assert.assertEquals(alert3.getId(), result.values().get(0).getId());
    }

    @Test
    public void testQueryWithContainsAndClusterClause() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .setMetric("my-contained-metric")
                .setCluster("my-cluster")
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .setService("my-contained-service")
                .setCluster("my-cluster")
                .build();
        final Alert alert3 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .build();
        alertRepo.addOrUpdateAlert(alert1, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert2, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert3, Organization.DEFAULT);
        final AlertQuery query = new DefaultAlertQuery(alertRepo, Organization.DEFAULT);
        query.contains(Optional.of("contained"));
        query.cluster(Optional.of("my-cluster"));
        final QueryResult<Alert> result = alertRepo.query(query);
        Assert.assertEquals(2, result.values().size());
        Assert.assertEquals(alert1.getId(), result.values().get(0).getId());
        Assert.assertEquals(alert2.getId(), result.values().get(1).getId());
    }

    @Test
    public void testQueryWithContainsAndServiceClause() {
        final Alert alert1 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .setMetric("my-contained-metric")
                .setService("my-service")
                .build();
        final Alert alert2 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .setCluster("my-contained-cluster")
                .setService("my-service")
                .build();
        final Alert alert3 = TestBeanFactory.createAlertBuilder()
                .setId(UUID.randomUUID())
                .build();
        alertRepo.addOrUpdateAlert(alert1, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert2, Organization.DEFAULT);
        alertRepo.addOrUpdateAlert(alert3, Organization.DEFAULT);
        final AlertQuery query = new DefaultAlertQuery(alertRepo, Organization.DEFAULT);
        query.contains(Optional.of("contained"));
        query.service(Optional.of("my-service"));
        final QueryResult<Alert> result = alertRepo.query(query);
        Assert.assertEquals(2, result.values().size());
        Assert.assertEquals(alert1.getId(), result.values().get(0).getId());
        Assert.assertEquals(alert2.getId(), result.values().get(1).getId());
    }

    private void assertAlertEbeanEquivalent(final Alert alert, final models.ebean.Alert ebeanAlert) {
        Assert.assertEquals(alert.getId(), ebeanAlert.getUuid());
        Assert.assertEquals(alert.getCluster(), ebeanAlert.getCluster());
        Assert.assertEquals(alert.getMetric(), ebeanAlert.getMetric());
        assertNagiosExtensionEbeanEquivalent(alert.getNagiosExtension(), ebeanAlert.getNagiosExtension());
        Assert.assertEquals(alert.getService(), ebeanAlert.getService());
        Assert.assertEquals(alert.getName(), ebeanAlert.getName());
        Assert.assertEquals(alert.getOperator(), ebeanAlert.getOperator());
        Assert.assertEquals(alert.getPeriod(), Period.seconds(ebeanAlert.getPeriod()).normalizedStandard());
        Assert.assertEquals(alert.getStatistic(), ebeanAlert.getStatistic());
        Assert.assertEquals(alert.getValue().getUnit(), Optional.of(ebeanAlert.getQuantityUnit()));
        Assert.assertEquals(alert.getValue().getValue(), ebeanAlert.getQuantityValue(), 0.001);
        Assert.assertEquals(alert.getContext(), ebeanAlert.getContext());
    }

    private static void assertNagiosExtensionEbeanEquivalent(
            final NagiosExtension extension,
            final models.ebean.NagiosExtension ebeanExtension) {
        Assert.assertEquals(extension.getSeverity(), ebeanExtension.getSeverity());
        Assert.assertEquals(extension.getNotify(), ebeanExtension.getNotify());
        Assert.assertEquals(extension.getMaxCheckAttempts(), ebeanExtension.getMaxCheckAttempts());
        Assert.assertEquals(extension.getFreshnessThreshold().getStandardSeconds(), ebeanExtension.getFreshnessThreshold());
    }

    private final DatabaseAlertRepository.AlertQueryGenerator queryGenerator = new DatabaseAlertRepository.GenericQueryGenerator();
    private final DatabaseAlertRepository alertRepo = new DatabaseAlertRepository(queryGenerator);
}
