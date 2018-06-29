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
package com.arpnetworking.metrics.portal.alerts.impl;

import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.CassandraConnectionFactory;
import com.arpnetworking.metrics.portal.TestBeanFactory;
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
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.thrift.transport.TTransportException;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.hamcrest.Matchers;
import org.joda.time.Period;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import play.Application;
import play.inject.Injector;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.WithApplication;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tests class <code>CassandraAlertRepository</code>.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class CassandraAlertRepositoryTest extends WithApplication {

    @Override
    protected Application provideApplication() {
        final String clusterName = EmbeddedCassandraServerHelper.getClusterName();
        final int port = EmbeddedCassandraServerHelper.getNativeTransportPort();
        final String host = EmbeddedCassandraServerHelper.getHost();
        _app = new GuiceApplicationBuilder()
                .configure(AkkaClusteringConfigFactory.generateConfiguration())
                .configure(CassandraConnectionFactory.generateConfiguration(clusterName, "portal", host, port))
                .build();
        return _app;
    }

    @BeforeClass
    public static void setupFixture() throws ConfigurationException, IOException, TTransportException {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra(EmbeddedCassandraServerHelper.CASSANDRA_RNDPORT_YML_FILE, 30000);
    }


    @Before
    public void setup() {
        final Injector injector = _app.injector();
        _mappingManager = injector.instanceOf(MappingManager.class);
        _alertRepo = injector.instanceOf(CassandraAlertRepository.class);
        _alertRepo.open();
    }

    @After
    public void teardown() {
        if (_alertRepo != null) {
            _alertRepo.close();
        }
        final int maxTries = 10;
        for (int x = 1; x <= maxTries; x++) {
            try {
                EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
                break;
            } catch (final Throwable e) {
                if (x == maxTries) {
                    throw e;
                }
            }

        }
    }

    @Test
    public void testGetForInvalidId() {
        Assert.assertFalse(_alertRepo.get(UUID.randomUUID(), Organization.DEFAULT).isPresent());
    }

    @Test
    public void testGetForValidId() throws IOException {
        final UUID uuid = UUID.randomUUID();
        final models.cassandra.Alert cassandraAlert = TestBeanFactory.createCassandraAlert();
        final Organization org = TestBeanFactory.organizationFrom(cassandraAlert.getOrganization());
        Assert.assertFalse(_alertRepo.get(uuid, org).isPresent());
        cassandraAlert.setUuid(uuid);

        final Mapper<models.cassandra.Alert> mapper = _mappingManager.mapper(models.cassandra.Alert.class);
        mapper.save(cassandraAlert);

        final Optional<Alert> expected = _alertRepo.get(uuid, org);
        Assert.assertTrue(expected.isPresent());
        assertAlertCassandraEquivalent(expected.get(), cassandraAlert);
    }

    @Test
    public void testGetAlertCountWithNoAlert() {
        Assert.assertEquals(0, _alertRepo.getAlertCount(Organization.DEFAULT));
    }

    @Test
    public void testGetAlertCountWithMultipleAlert() throws JsonProcessingException {
        Assert.assertEquals(0, _alertRepo.getAlertCount(Organization.DEFAULT));
        final Mapper<models.cassandra.Alert> mapper = _mappingManager.mapper(models.cassandra.Alert.class);

        final Organization org = new DefaultOrganization.Builder().setId(UUID.randomUUID()).build();

        final models.cassandra.Alert cassandraAlert1 = TestBeanFactory.createCassandraAlert();
        cassandraAlert1.setOrganization(org.getId());
        mapper.save(cassandraAlert1);

        final models.cassandra.Alert cassandraAlert2 = TestBeanFactory.createCassandraAlert();
        cassandraAlert2.setOrganization(org.getId());
        mapper.save(cassandraAlert2);

        Assert.assertEquals(2, _alertRepo.getAlertCount(org));
    }

    @Test
    public void testAddOrUpdateAlertAddCase() {
        final UUID uuid = UUID.randomUUID();
        Assert.assertFalse(_alertRepo.get(uuid, Organization.DEFAULT).isPresent());
        final Alert actualAlert = TestBeanFactory.createAlertBuilder().setId(uuid).build();
        _alertRepo.addOrUpdateAlert(actualAlert, Organization.DEFAULT);
        final Optional<Alert> expected = _alertRepo.get(uuid, Organization.DEFAULT);
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
        _alertRepo.addOrUpdateAlert(alert, Organization.DEFAULT);
        final Alert expectedAlert = _alertRepo.get(uuid, Organization.DEFAULT).get();
        Assert.assertNull(expectedAlert.getNagiosExtension());
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
        _alertRepo.addOrUpdateAlert(alert1, Organization.DEFAULT);
        _alertRepo.addOrUpdateAlert(alert2, Organization.DEFAULT);
        final AlertQuery successQuery = new DefaultAlertQuery(_alertRepo, Organization.DEFAULT);
        successQuery.cluster(Optional.of("my-test-cluster"));
        final QueryResult<Alert> successResult = _alertRepo.query(successQuery);
        Assert.assertEquals(1, successResult.total());
        final AlertQuery failQuery = new DefaultAlertQuery(_alertRepo, Organization.DEFAULT);
        failQuery.cluster(Optional.of("some-random-cluster"));
        final QueryResult<Alert> failResult = _alertRepo.query(failQuery);
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
        _alertRepo.addOrUpdateAlert(alert1, Organization.DEFAULT);
        _alertRepo.addOrUpdateAlert(alert2, Organization.DEFAULT);
        final AlertQuery successQuery = new DefaultAlertQuery(_alertRepo, Organization.DEFAULT);
        successQuery.context(Optional.of(Context.CLUSTER));
        final QueryResult<Alert> successResult = _alertRepo.query(successQuery);
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
        _alertRepo.addOrUpdateAlert(alert1, Organization.DEFAULT);
        _alertRepo.addOrUpdateAlert(alert2, Organization.DEFAULT);
        final AlertQuery successQuery = new DefaultAlertQuery(_alertRepo, Organization.DEFAULT);
        successQuery.service(Optional.of("my-test-service"));
        final QueryResult<Alert> successResult = _alertRepo.query(successQuery);
        Assert.assertEquals(1, successResult.total());
        final AlertQuery failQuery = new DefaultAlertQuery(_alertRepo, Organization.DEFAULT);
        failQuery.service(Optional.of("some-random-service"));
        final QueryResult<Alert> failResult = _alertRepo.query(failQuery);
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
        _alertRepo.addOrUpdateAlert(alert1, Organization.DEFAULT);
        _alertRepo.addOrUpdateAlert(alert2, Organization.DEFAULT);
        _alertRepo.addOrUpdateAlert(alert3, Organization.DEFAULT);
        _alertRepo.addOrUpdateAlert(alert4, Organization.DEFAULT);
        _alertRepo.addOrUpdateAlert(alert5, Organization.DEFAULT);
        final AlertQuery successQuery = new DefaultAlertQuery(_alertRepo, Organization.DEFAULT);
        successQuery.contains(Optional.of("contained"));
        final QueryResult<Alert> successResult = _alertRepo.query(successQuery);
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
        _alertRepo.addOrUpdateAlert(alert1, Organization.DEFAULT);
        _alertRepo.addOrUpdateAlert(alert2, Organization.DEFAULT);
        final AlertQuery query1 = new DefaultAlertQuery(_alertRepo, Organization.DEFAULT);
        query1.service(Optional.of("my-test-service"));
        query1.cluster(Optional.of("my-test-cluster"));
        query1.limit(1);
        final QueryResult<Alert> result1 = _alertRepo.query(query1);
        Assert.assertEquals(1, result1.values().size());
        final AlertQuery query2 = new DefaultAlertQuery(_alertRepo, Organization.DEFAULT);
        query2.service(Optional.of("my-test-service"));
        query2.cluster(Optional.of("my-test-cluster"));
        query2.limit(2);
        final QueryResult<Alert> result2 = _alertRepo.query(query2);
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
        _alertRepo.addOrUpdateAlert(alert1, Organization.DEFAULT);
        _alertRepo.addOrUpdateAlert(alert2, Organization.DEFAULT);
        _alertRepo.addOrUpdateAlert(alert3, Organization.DEFAULT);
        final AlertQuery query = new DefaultAlertQuery(_alertRepo, Organization.DEFAULT);
        query.service(Optional.of("my-test-service"));
        query.cluster(Optional.of("my-test-cluster"));
        query.offset(Optional.of(2));
        query.limit(2);
        final QueryResult<Alert> result = _alertRepo.query(query);
        Assert.assertEquals(1, result.values().size());
        Assert.assertThat(result.values().get(0).getId(), Matchers.anyOf(
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
        _alertRepo.addOrUpdateAlert(alert1, Organization.DEFAULT);
        _alertRepo.addOrUpdateAlert(alert2, Organization.DEFAULT);
        _alertRepo.addOrUpdateAlert(alert3, Organization.DEFAULT);
        final AlertQuery query = new DefaultAlertQuery(_alertRepo, Organization.DEFAULT);
        query.contains(Optional.of("contained"));
        query.cluster(Optional.of("my-cluster"));
        final QueryResult<Alert> result = _alertRepo.query(query);
        Assert.assertEquals(2, result.values().size());
        Assert.assertTrue(result.values().stream().anyMatch(i -> i.getId().equals(alert1.getId())));
        Assert.assertTrue(result.values().stream().anyMatch(i -> i.getId().equals(alert2.getId())));
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
        _alertRepo.addOrUpdateAlert(alert1, Organization.DEFAULT);
        _alertRepo.addOrUpdateAlert(alert2, Organization.DEFAULT);
        _alertRepo.addOrUpdateAlert(alert3, Organization.DEFAULT);
        final AlertQuery query = new DefaultAlertQuery(_alertRepo, Organization.DEFAULT);
        query.contains(Optional.of("contained"));
        query.service(Optional.of("my-service"));
        final QueryResult<Alert> result = _alertRepo.query(query);
        Assert.assertEquals(2, result.values().size());
        Assert.assertTrue(result.values().stream().anyMatch(i -> i.getId().equals(alert1.getId())));
        Assert.assertTrue(result.values().stream().anyMatch(i -> i.getId().equals(alert2.getId())));
    }

    private void assertAlertCassandraEquivalent(final Alert alert, final models.cassandra.Alert cassandraAlert) {
        Assert.assertEquals(alert.getId(), cassandraAlert.getUuid());
        Assert.assertEquals(alert.getCluster(), cassandraAlert.getCluster());
        Assert.assertEquals(alert.getMetric(), cassandraAlert.getMetric());
        assertNagiosExtensionCassandraEquivalent(alert.getNagiosExtension(), cassandraAlert.getNagiosExtensions());
        Assert.assertEquals(alert.getService(), cassandraAlert.getService());
        Assert.assertEquals(alert.getName(), cassandraAlert.getName());
        Assert.assertEquals(alert.getOperator(), cassandraAlert.getOperator());
        Assert.assertEquals(alert.getPeriod(), Period.seconds(cassandraAlert.getPeriodInSeconds()).normalizedStandard());
        Assert.assertEquals(alert.getStatistic(), cassandraAlert.getStatistic());
        Assert.assertEquals(alert.getValue().getUnit(), Optional.of(cassandraAlert.getQuantityUnit()));
        Assert.assertEquals(alert.getValue().getValue(), cassandraAlert.getQuantityValue(), 0.001);
        Assert.assertEquals(alert.getContext(), cassandraAlert.getContext());
    }

    private static void assertNagiosExtensionCassandraEquivalent(
            final NagiosExtension extension,
            final Map<String, String> cassExtension) {
        Assert.assertEquals(extension.getSeverity(), cassExtension.get("severity"));
        Assert.assertEquals(extension.getNotify(), cassExtension.get("notify"));
        Assert.assertEquals(extension.getMaxCheckAttempts(), Integer.parseInt(cassExtension.get("attempts")));
        Assert.assertEquals(extension.getFreshnessThreshold().getStandardSeconds(), Long.parseLong(cassExtension.get("freshness")));
    }

    private CassandraAlertRepository _alertRepo;
    private MappingManager _mappingManager;
    private Application _app;
}
