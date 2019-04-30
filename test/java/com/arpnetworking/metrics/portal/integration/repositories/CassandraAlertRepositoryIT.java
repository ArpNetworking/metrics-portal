/*
 * Copyright 2017 Smartsheet.com
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
import com.arpnetworking.metrics.portal.alerts.impl.CassandraAlertRepository;
import com.datastax.driver.core.Session;
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import models.internal.Alert;
import models.internal.AlertQuery;
import models.internal.Context;
import models.internal.NagiosExtension;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultAlertQuery;
import models.internal.impl.DefaultOrganization;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Tests class {@link CassandraAlertRepository}.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class CassandraAlertRepositoryIT {

    @Before
    public void setUp() {
        final Session cassandraSession = null;
        final MappingManager mappingManager = null;
        _alertRepo = new CassandraAlertRepository(cassandraSession, mappingManager);
        _alertRepo.open();
    }

    @After
    public void tearDown() {
        _alertRepo.close();
    }

    @Test
    public void testGetForInvalidId() {
        assertFalse(_alertRepo.getAlert(UUID.randomUUID(), TestBeanFactory.getDefautOrganization()).isPresent());
    }

    @Test
    public void testGetForValidId() throws IOException {
        final UUID uuid = UUID.randomUUID();
        final models.cassandra.Alert cassandraAlert = TestBeanFactory.createCassandraAlert();
        final Organization org = TestBeanFactory.organizationFrom(cassandraAlert.getOrganization());
        assertFalse(_alertRepo.getAlert(uuid, org).isPresent());
        cassandraAlert.setUuid(uuid);

        final Mapper<models.cassandra.Alert> mapper = _mappingManager.mapper(models.cassandra.Alert.class);
        mapper.save(cassandraAlert);

        final Optional<Alert> expected = _alertRepo.getAlert(uuid, org);
        assertTrue(expected.isPresent());
        assertAlertCassandraEquivalent(expected.get(), cassandraAlert);
    }

    @Test
    public void testGetAlertCountWithNoAlert() {
        assertEquals(0, _alertRepo.getAlertCount(TestBeanFactory.getDefautOrganization()));
    }

    @Test
    public void testGetAlertCountWithMultipleAlert() throws JsonProcessingException {
        assertEquals(0, _alertRepo.getAlertCount(TestBeanFactory.getDefautOrganization()));
        final Mapper<models.cassandra.Alert> mapper = _mappingManager.mapper(models.cassandra.Alert.class);

        final Organization org = new DefaultOrganization.Builder().setId(UUID.randomUUID()).build();

        final models.cassandra.Alert cassandraAlert1 = TestBeanFactory.createCassandraAlert();
        cassandraAlert1.setOrganization(org.getId());
        mapper.save(cassandraAlert1);

        final models.cassandra.Alert cassandraAlert2 = TestBeanFactory.createCassandraAlert();
        cassandraAlert2.setOrganization(org.getId());
        mapper.save(cassandraAlert2);

        assertEquals(2, _alertRepo.getAlertCount(org));
    }

    @Test
    public void testAddOrUpdateAlertAddCase() {
        final UUID uuid = UUID.randomUUID();
        assertFalse(_alertRepo.getAlert(uuid, TestBeanFactory.getDefautOrganization()).isPresent());
        final Alert actualAlert = TestBeanFactory.createAlertBuilder().setId(uuid).build();
        _alertRepo.addOrUpdateAlert(actualAlert, TestBeanFactory.getDefautOrganization());
        final Optional<Alert> expected = _alertRepo.getAlert(uuid, TestBeanFactory.getDefautOrganization());
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
        final Alert expectedAlert = _alertRepo.getAlert(uuid, TestBeanFactory.getDefautOrganization()).get();
        assertNull(expectedAlert.getNagiosExtension());
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
        final QueryResult<Alert> successResult = _alertRepo.queryAlerts(successQuery);
        assertEquals(1, successResult.total());
        final AlertQuery failQuery = new DefaultAlertQuery(_alertRepo, TestBeanFactory.getDefautOrganization());
        failQuery.cluster(Optional.of("some-random-cluster"));
        final QueryResult<Alert> failResult = _alertRepo.queryAlerts(failQuery);
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
        final QueryResult<Alert> successResult = _alertRepo.queryAlerts(successQuery);
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
        final QueryResult<Alert> successResult = _alertRepo.queryAlerts(successQuery);
        assertEquals(1, successResult.total());
        final AlertQuery failQuery = new DefaultAlertQuery(_alertRepo, TestBeanFactory.getDefautOrganization());
        failQuery.service(Optional.of("some-random-service"));
        final QueryResult<Alert> failResult = _alertRepo.queryAlerts(failQuery);
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
        final QueryResult<Alert> successResult = _alertRepo.queryAlerts(successQuery);
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
        final QueryResult<Alert> result1 = _alertRepo.queryAlerts(query1);
        assertEquals(1, result1.values().size());
        final AlertQuery query2 = new DefaultAlertQuery(_alertRepo, TestBeanFactory.getDefautOrganization());
        query2.service(Optional.of("my-test-service"));
        query2.cluster(Optional.of("my-test-cluster"));
        query2.limit(2);
        final QueryResult<Alert> result2 = _alertRepo.queryAlerts(query2);
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
        final QueryResult<Alert> result = _alertRepo.queryAlerts(query);
        assertEquals(1, result.values().size());
        assertThat(result.values().get(0).getId(), Matchers.anyOf(
                Matchers.equalTo(alert1.getId()),
                Matchers.equalTo(alert2.getId()),
                Matchers.equalTo(alert3.getId())));
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
        final QueryResult<Alert> result = _alertRepo.queryAlerts(query);
        assertEquals(2, result.values().size());
        assertTrue(result.values().stream().anyMatch(i -> i.getId().equals(alert1.getId())));
        assertTrue(result.values().stream().anyMatch(i -> i.getId().equals(alert2.getId())));
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
        final QueryResult<Alert> result = _alertRepo.queryAlerts(query);
        assertEquals(2, result.values().size());
        assertTrue(result.values().stream().anyMatch(i -> i.getId().equals(alert1.getId())));
        assertTrue(result.values().stream().anyMatch(i -> i.getId().equals(alert2.getId())));
    }

    private void assertAlertCassandraEquivalent(final Alert alert, final models.cassandra.Alert cassandraAlert) {
        assertEquals(alert.getId(), cassandraAlert.getUuid());
        assertEquals(alert.getCluster(), cassandraAlert.getCluster());
        assertEquals(alert.getMetric(), cassandraAlert.getMetric());
        assertNagiosExtensionCassandraEquivalent(alert.getNagiosExtension(), cassandraAlert.getNagiosExtensions());
        assertEquals(alert.getService(), cassandraAlert.getService());
        assertEquals(alert.getName(), cassandraAlert.getName());
        assertEquals(alert.getOperator(), cassandraAlert.getOperator());
        assertEquals(alert.getPeriod(), Duration.ofSeconds(cassandraAlert.getPeriodInSeconds()));
        assertEquals(alert.getStatistic(), cassandraAlert.getStatistic());
        assertEquals(alert.getValue().getUnit(), Optional.of(cassandraAlert.getQuantityUnit()));
        assertEquals(alert.getValue().getValue(), cassandraAlert.getQuantityValue(), 0.001);
        assertEquals(alert.getContext(), cassandraAlert.getContext());
    }

    private static void assertNagiosExtensionCassandraEquivalent(
            final NagiosExtension extension,
            final Map<String, String> cassExtension) {
        assertEquals(extension.getSeverity(), cassExtension.get("severity"));
        assertEquals(extension.getNotify(), cassExtension.get("notify"));
        assertEquals(extension.getMaxCheckAttempts(), Integer.parseInt(cassExtension.get("attempts")));
        assertEquals(extension.getFreshnessThreshold().getSeconds(), Long.parseLong(cassExtension.get("freshness")));
    }

    private CassandraAlertRepository _alertRepo;
    private MappingManager _mappingManager;
}
