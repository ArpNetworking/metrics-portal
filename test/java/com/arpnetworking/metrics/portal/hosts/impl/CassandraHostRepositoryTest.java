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
package com.arpnetworking.metrics.portal.hosts.impl;

import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.CassandraConnectionFactory;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.typesafe.config.ConfigFactory;
import models.internal.Host;
import models.internal.HostQuery;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultOrganization;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.thrift.transport.TTransportException;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import play.Application;
import play.inject.Injector;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.WithApplication;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * Tests class {@link com.arpnetworking.metrics.portal.alerts.impl.CassandraAlertRepository}.
 *
 * TODO(ville): Convert this to an integration test.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
@Ignore
@SuppressWarnings("deprecation")
public final class CassandraHostRepositoryTest extends WithApplication {

    @Override
    protected Application provideApplication() {
        final String clusterName = EmbeddedCassandraServerHelper.getClusterName();
        final int port = EmbeddedCassandraServerHelper.getNativeTransportPort();
        final String host = EmbeddedCassandraServerHelper.getHost();
        _app = new GuiceApplicationBuilder()
                .loadConfig(ConfigFactory.load("portal.application.conf"))
                .configure("hostRepository.type", CassandraHostRepository.class.getName())
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
    public void setUp() {
        final Injector injector = _app.injector();
        _mappingManager = injector.instanceOf(MappingManager.class);
        _hostRepo = injector.instanceOf(CassandraHostRepository.class);
        _hostRepo.open();
    }

    @After
    public void tearDown() {
        if (_hostRepo != null) {
            _hostRepo.close();
        }
        final int maxTries = 10;
        for (int x = 1; x <= maxTries; x++) {
            try {
                EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
                break;
                // CHECKSTYLE.OFF: IllegalCatch - Retry any runtime exceptions
            } catch (final RuntimeException e) {
                // CHECKSTYLE.ON
                if (x == maxTries) {
                    throw e;
                }
            }

        }
    }

    @Test
    public void testQueryForInvalidHost() {
        final HostQuery query = _hostRepo.createHostQuery(TestBeanFactory.getDefautOrganization());
        query.partialHostname(Optional.of(UUID.randomUUID().toString()));
        assertEquals(0L, _hostRepo.queryHosts(query).total());
    }

    @Test
    public void testQueryForValidName() throws IOException {
        final String name = "testqueryvalid.example.com";
        final models.cassandra.Host cassandraHost = TestBeanFactory.createCassandraHost();
        cassandraHost.setName(name);
        final Organization org = TestBeanFactory.organizationFrom(cassandraHost.getOrganization());

        final HostQuery query = _hostRepo.createHostQuery(TestBeanFactory.getDefautOrganization());
        query.partialHostname(Optional.of(cassandraHost.getName()));
        assertEquals(0L, _hostRepo.queryHosts(query).total());

        final Mapper<models.cassandra.Host> mapper = _mappingManager.mapper(models.cassandra.Host.class);
        mapper.save(cassandraHost);

        final QueryResult<Host> result = _hostRepo.queryHosts(query);
        assertEquals(1L, result.total());
        assertHostCassandraEquivalent(result.values().get(0), cassandraHost);
    }

    @Test
    public void testGetHostCountWithNoHost() {
        assertEquals(0, _hostRepo.getHostCount(new DefaultOrganization.Builder().setId(UUID.randomUUID()).build()));
    }

    @Test
    public void testGetHostCountWithMultipleHost() throws JsonProcessingException {
        final Organization org = new DefaultOrganization.Builder().setId(UUID.randomUUID()).build();
        assertEquals(0, _hostRepo.getHostCount(org));
        final Mapper<models.cassandra.Host> mapper = _mappingManager.mapper(models.cassandra.Host.class);

        final models.cassandra.Host cassandraHost1 = TestBeanFactory.createCassandraHost();
        cassandraHost1.setOrganization(org.getId());
        mapper.save(cassandraHost1);

        final models.cassandra.Host cassandraHost = TestBeanFactory.createCassandraHost();
        cassandraHost.setOrganization(org.getId());
        mapper.save(cassandraHost);

        assertEquals(2, _hostRepo.getHostCount(org));
    }

//    @Test
//    public void testAddOrUpdateAlertAddCase() {
//        final UUID uuid = UUID.randomUUID();
//        Assert.assertFalse(_alertRepo.get(uuid, TestBeanFactory.getDefautOrganization()).isPresent());
//        final Alert actualAlert = TestBeanFactory.createAlertBuilder().setId(uuid).build();
//        _alertRepo.addOrUpdateAlert(actualAlert, TestBeanFactory.getDefautOrganization());
//        final Optional<Alert> expected = _alertRepo.get(uuid, TestBeanFactory.getDefautOrganization());
//        Assert.assertTrue(expected.isPresent());
//        Assert.assertEquals(expected.get(), actualAlert);
//    }
//
//    @Test
//    public void testQueryClauseWithLimit() {
//        final Alert alert1 = TestBeanFactory.createAlertBuilder()
//                .setId(UUID.randomUUID())
//                .build();
//        final Alert alert2 = TestBeanFactory.createAlertBuilder()
//                .setId(UUID.randomUUID())
//                .build();
//        _alertRepo.addOrUpdateAlert(alert1, TestBeanFactory.getDefautOrganization());
//        _alertRepo.addOrUpdateAlert(alert2, TestBeanFactory.getDefautOrganization());
//        final AlertQuery query1 = new DefaultAlertQuery(_alertRepo, TestBeanFactory.getDefautOrganization());
//        query1.limit(1);
//        final QueryResult<Alert> result1 = _alertRepo.query(query1);
//        Assert.assertEquals(1, result1.values().size());
//        final AlertQuery query2 = new DefaultAlertQuery(_alertRepo, TestBeanFactory.getDefautOrganization());
//        query2.limit(2);
//        final QueryResult<Alert> result2 = _alertRepo.query(query2);
//        Assert.assertEquals(2, result2.values().size());
//    }
//
//    @Test
//    public void testQueryClauseWithOffsetAndLimit() {
//        final Alert alert1 = TestBeanFactory.createAlertBuilder()
//                .setId(UUID.randomUUID())
//                .build();
//        final Alert alert2 = TestBeanFactory.createAlertBuilder()
//                .setId(UUID.randomUUID())
//                .build();
//        final Alert alert3 = TestBeanFactory.createAlertBuilder()
//                .setId(UUID.randomUUID())
//                .build();
//        _alertRepo.addOrUpdateAlert(alert1, TestBeanFactory.getDefautOrganization());
//        _alertRepo.addOrUpdateAlert(alert2, TestBeanFactory.getDefautOrganization());
//        _alertRepo.addOrUpdateAlert(alert3, TestBeanFactory.getDefautOrganization());
//        final AlertQuery query = new DefaultAlertQuery(_alertRepo, TestBeanFactory.getDefautOrganization());
//        query.offset(Optional.of(2));
//        query.limit(2);
//        final QueryResult<Alert> result = _alertRepo.query(query);
//        Assert.assertEquals(1, result.values().size());
//        Assert.assertThat(result.values().get(0).getId(), Matchers.anyOf(
//                Matchers.equalTo(alert1.getId()),
//                Matchers.equalTo(alert2.getId()),
//                Matchers.equalTo(alert3.getId())));
//    }

    private void assertHostCassandraEquivalent(final Host host, final models.cassandra.Host cassandraHost) {
        assertEquals(host.getHostname(), cassandraHost.getName());
        assertEquals(host.getCluster().orElse(null), cassandraHost.getCluster());
        assertEquals(host.getMetricsSoftwareState().name(), cassandraHost.getMetricsSoftwareState());
    }

    private CassandraHostRepository _hostRepo;
    private MappingManager _mappingManager;
    private Application _app;
}
