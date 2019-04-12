/*
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

import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.H2ConnectionStringFactory;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.typesafe.config.ConfigFactory;
import io.ebean.Ebean;
import io.ebean.Transaction;
import models.internal.Alert;
import models.internal.AlertQuery;
import models.internal.Context;
import models.internal.NagiosExtension;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultAlertQuery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.WithApplication;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import javax.persistence.PersistenceException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests class <code>DatabaseAlertRepository</code>.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
public class DatabaseAlertRepositoryTest extends WithApplication {

    @Before
    public void setUp() {
        _alertRepo.open();
    }

    @After
    public void tearDown() {
        _alertRepo.close();
    }

    @Test
    public void testGetForInvalidId() {
        assertFalse(_alertRepo.get(UUID.randomUUID(), TestBeanFactory.getDefautOrganization()).isPresent());
    }

    @Override
    protected Application provideApplication() {
        return new GuiceApplicationBuilder()
                .loadConfig(ConfigFactory.load("portal.application.conf"))
                .configure("alertRepository.type", DatabaseAlertRepository.class.getName())
                .configure("alertRepository.expressionQueryGenerator.type", DatabaseAlertRepository.GenericQueryGenerator.class.getName())
                .configure("play.modules.disabled", Arrays.asList("play.core.ObjectMapperModule", "global.PillarModule"))
                .configure(AkkaClusteringConfigFactory.generateConfiguration())
                .configure(H2ConnectionStringFactory.generateConfiguration())
                .build();
    }

    @Test
    public void testGetForValidId() throws IOException {
        final UUID uuid = UUID.randomUUID();
        final models.ebean.Alert ebeanAlert = TestBeanFactory.createEbeanAlert();
        final Organization org = TestBeanFactory.organizationFrom(ebeanAlert.getOrganization());
        assertFalse(_alertRepo.get(uuid, org).isPresent());
        ebeanAlert.setUuid(uuid);
        try (Transaction transaction = Ebean.beginTransaction()) {
            Ebean.save(ebeanAlert);
            transaction.commit();
        }
        final Optional<Alert> expected = _alertRepo.get(uuid, org);
        assertTrue(expected.isPresent());
        assertAlertEbeanEquivalent(expected.get(), ebeanAlert);
    }

    @Test
    public void testGetAlertCountWithNoAlert() {
        assertEquals(0, _alertRepo.getAlertCount(TestBeanFactory.getDefautOrganization()));
    }

    @Test
    public void testGetAlertCountWithMultipleAlert() throws JsonProcessingException {
        assertEquals(0, _alertRepo.getAlertCount(TestBeanFactory.getDefautOrganization()));
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
        assertEquals(2, _alertRepo.getAlertCount(org));
    }

    @Test
    public void testAddOrUpdateAlertAddCase() {
        final UUID uuid = UUID.randomUUID();
        assertFalse(_alertRepo.get(uuid, TestBeanFactory.getDefautOrganization()).isPresent());
        final Alert actualAlert = TestBeanFactory.createAlertBuilder().setId(uuid).build();
        _alertRepo.addOrUpdateAlert(actualAlert, TestBeanFactory.getDefautOrganization());
        final Optional<Alert> expected = _alertRepo.get(uuid, TestBeanFactory.getDefautOrganization());
        assertTrue(expected.isPresent());
        assertEquals(expected.get(), actualAlert);
    }

    @Test
    public void testAddAlertWithNoExtension() {
        final UUID uuid = UUID.randomUUID();
        final Alert alert = TestBeanFactory.createAlertBuilder()
                .setId(uuid)
                .setNagiosExtension(null)
                .build();
        _alertRepo.addOrUpdateAlert(alert, TestBeanFactory.getDefautOrganization());
        final Alert expectedAlert = _alertRepo.get(uuid, TestBeanFactory.getDefautOrganization()).get();
        assertNull(expectedAlert.getNagiosExtension());
    }

    @Test(expected = PersistenceException.class)
    public void testThrowsExceptionWhenQueryFails() throws IOException {
        final UUID uuid = UUID.randomUUID();
        _alertRepo.addOrUpdateAlert(TestBeanFactory.createAlertBuilder()
                .setId(uuid)
                .setCluster("new-cluster")
                .build(), TestBeanFactory.getDefautOrganization());
        final models.ebean.Alert ebeanAlert1 = Ebean.find(models.ebean.Alert.class)
                .where()
                .eq("uuid", uuid)
                .findOne();
        final models.ebean.Alert ebeanAlert2 = Ebean.find(models.ebean.Alert.class)
                .where()
                .eq("uuid", uuid)
                .findOne();
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
        _alertRepo.addOrUpdateAlert(alert1, TestBeanFactory.getDefautOrganization());
        _alertRepo.addOrUpdateAlert(alert2, TestBeanFactory.getDefautOrganization());
        final AlertQuery successQuery = new DefaultAlertQuery(_alertRepo, TestBeanFactory.getDefautOrganization());
        successQuery.cluster(Optional.of("my-test-cluster"));
        final QueryResult<Alert> successResult = _alertRepo.query(successQuery);
        assertEquals(1, successResult.total());
        final AlertQuery failQuery = new DefaultAlertQuery(_alertRepo, TestBeanFactory.getDefautOrganization());
        failQuery.cluster(Optional.of("some-random-cluster"));
        final QueryResult<Alert> failResult = _alertRepo.query(failQuery);
        assertEquals(0, failResult.total());
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
        _alertRepo.addOrUpdateAlert(alert1, TestBeanFactory.getDefautOrganization());
        _alertRepo.addOrUpdateAlert(alert2, TestBeanFactory.getDefautOrganization());
        final AlertQuery successQuery = new DefaultAlertQuery(_alertRepo, TestBeanFactory.getDefautOrganization());
        successQuery.context(Optional.of(Context.CLUSTER));
        final QueryResult<Alert> successResult = _alertRepo.query(successQuery);
        assertEquals(1, successResult.total());
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
        _alertRepo.addOrUpdateAlert(alert1, TestBeanFactory.getDefautOrganization());
        _alertRepo.addOrUpdateAlert(alert2, TestBeanFactory.getDefautOrganization());
        final AlertQuery successQuery = new DefaultAlertQuery(_alertRepo, TestBeanFactory.getDefautOrganization());
        successQuery.service(Optional.of("my-test-service"));
        final QueryResult<Alert> successResult = _alertRepo.query(successQuery);
        assertEquals(1, successResult.total());
        final AlertQuery failQuery = new DefaultAlertQuery(_alertRepo, TestBeanFactory.getDefautOrganization());
        failQuery.service(Optional.of("some-random-service"));
        final QueryResult<Alert> failResult = _alertRepo.query(failQuery);
        assertEquals(0, failResult.total());
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
        _alertRepo.addOrUpdateAlert(alert1, TestBeanFactory.getDefautOrganization());
        _alertRepo.addOrUpdateAlert(alert2, TestBeanFactory.getDefautOrganization());
        _alertRepo.addOrUpdateAlert(alert3, TestBeanFactory.getDefautOrganization());
        _alertRepo.addOrUpdateAlert(alert4, TestBeanFactory.getDefautOrganization());
        _alertRepo.addOrUpdateAlert(alert5, TestBeanFactory.getDefautOrganization());
        final AlertQuery successQuery = new DefaultAlertQuery(_alertRepo, TestBeanFactory.getDefautOrganization());
        successQuery.contains(Optional.of("contained"));
        final QueryResult<Alert> successResult = _alertRepo.query(successQuery);
        assertEquals(3, successResult.total());
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
        _alertRepo.addOrUpdateAlert(alert1, TestBeanFactory.getDefautOrganization());
        _alertRepo.addOrUpdateAlert(alert2, TestBeanFactory.getDefautOrganization());
        final AlertQuery query1 = new DefaultAlertQuery(_alertRepo, TestBeanFactory.getDefautOrganization());
        query1.service(Optional.of("my-test-service"));
        query1.cluster(Optional.of("my-test-cluster"));
        query1.limit(1);
        final QueryResult<Alert> result1 = _alertRepo.query(query1);
        assertEquals(1, result1.values().size());
        final AlertQuery query2 = new DefaultAlertQuery(_alertRepo, TestBeanFactory.getDefautOrganization());
        query2.service(Optional.of("my-test-service"));
        query2.cluster(Optional.of("my-test-cluster"));
        query2.limit(2);
        final QueryResult<Alert> result2 = _alertRepo.query(query2);
        assertEquals(2, result2.values().size());
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
        _alertRepo.addOrUpdateAlert(alert1, TestBeanFactory.getDefautOrganization());
        _alertRepo.addOrUpdateAlert(alert2, TestBeanFactory.getDefautOrganization());
        _alertRepo.addOrUpdateAlert(alert3, TestBeanFactory.getDefautOrganization());
        final AlertQuery query = new DefaultAlertQuery(_alertRepo, TestBeanFactory.getDefautOrganization());
        query.service(Optional.of("my-test-service"));
        query.cluster(Optional.of("my-test-cluster"));
        query.offset(Optional.of(2));
        query.limit(2);
        final QueryResult<Alert> result = _alertRepo.query(query);
        assertEquals(1, result.values().size());
        assertEquals(alert3.getId(), result.values().get(0).getId());
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
        _alertRepo.addOrUpdateAlert(alert1, TestBeanFactory.getDefautOrganization());
        _alertRepo.addOrUpdateAlert(alert2, TestBeanFactory.getDefautOrganization());
        _alertRepo.addOrUpdateAlert(alert3, TestBeanFactory.getDefautOrganization());
        final AlertQuery query = new DefaultAlertQuery(_alertRepo, TestBeanFactory.getDefautOrganization());
        query.contains(Optional.of("contained"));
        query.cluster(Optional.of("my-cluster"));
        final QueryResult<Alert> result = _alertRepo.query(query);
        assertEquals(2, result.values().size());
        assertEquals(alert1.getId(), result.values().get(0).getId());
        assertEquals(alert2.getId(), result.values().get(1).getId());
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
        _alertRepo.addOrUpdateAlert(alert1, TestBeanFactory.getDefautOrganization());
        _alertRepo.addOrUpdateAlert(alert2, TestBeanFactory.getDefautOrganization());
        _alertRepo.addOrUpdateAlert(alert3, TestBeanFactory.getDefautOrganization());
        final AlertQuery query = new DefaultAlertQuery(_alertRepo, TestBeanFactory.getDefautOrganization());
        query.contains(Optional.of("contained"));
        query.service(Optional.of("my-service"));
        final QueryResult<Alert> result = _alertRepo.query(query);
        assertEquals(2, result.values().size());
        assertEquals(alert1.getId(), result.values().get(0).getId());
        assertEquals(alert2.getId(), result.values().get(1).getId());
    }

    private void assertAlertEbeanEquivalent(final Alert alert, final models.ebean.Alert ebeanAlert) {
        assertEquals(alert.getId(), ebeanAlert.getUuid());
        assertEquals(alert.getCluster(), ebeanAlert.getCluster());
        assertEquals(alert.getMetric(), ebeanAlert.getMetric());
        assertNagiosExtensionEbeanEquivalent(alert.getNagiosExtension(), ebeanAlert.getNagiosExtension());
        assertEquals(alert.getService(), ebeanAlert.getService());
        assertEquals(alert.getName(), ebeanAlert.getName());
        assertEquals(alert.getOperator(), ebeanAlert.getOperator());
        assertEquals(alert.getPeriod(), Duration.ofSeconds(ebeanAlert.getPeriod()));
        assertEquals(alert.getStatistic(), ebeanAlert.getStatistic());
        assertEquals(alert.getValue().getUnit(), Optional.of(ebeanAlert.getQuantityUnit()));
        assertEquals(alert.getValue().getValue(), ebeanAlert.getQuantityValue(), 0.001);
        assertEquals(alert.getContext(), ebeanAlert.getContext());
    }

    private static void assertNagiosExtensionEbeanEquivalent(
            final NagiosExtension extension,
            final models.ebean.NagiosExtension ebeanExtension) {
        assertEquals(extension.getSeverity(), ebeanExtension.getSeverity());
        assertEquals(extension.getNotify(), ebeanExtension.getNotify());
        assertEquals(extension.getMaxCheckAttempts(), ebeanExtension.getMaxCheckAttempts());
        assertEquals(extension.getFreshnessThreshold().getSeconds(), ebeanExtension.getFreshnessThreshold());
    }

    private final DatabaseAlertRepository.AlertQueryGenerator _queryGenerator = new DatabaseAlertRepository.GenericQueryGenerator();
    private final DatabaseAlertRepository _alertRepo = new DatabaseAlertRepository(_queryGenerator);
}
