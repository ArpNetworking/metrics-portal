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
import com.arpnetworking.metrics.portal.hosts.impl.CassandraHostRepository;
import com.arpnetworking.metrics.portal.integration.test.CassandraServerHelper;
import com.datastax.oss.driver.api.core.CqlSession;
import models.internal.Host;
import models.internal.HostQuery;
import models.internal.Organization;
import models.internal.QueryResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * Tests class {@link com.arpnetworking.metrics.portal.hosts.impl.CassandraHostRepository}.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class CassandraHostRepositoryIT {

    @Before
    public void setUp() {
        _cassandraSession = CassandraServerHelper.createSession();
        _hostRepo = new CassandraHostRepository(_cassandraSession);
        _hostRepo.open();
    }

    @After
    public void tearDown() {
        if (_hostRepo != null) {
            _hostRepo.close();
        }
    }

    @Test
    public void testQueryForInvalidHost() {
        final HostQuery query = _hostRepo.createHostQuery(TestBeanFactory.newOrganization());
        query.partialHostname(Optional.of(UUID.randomUUID().toString()));
        assertEquals(0L, _hostRepo.queryHosts(query).total());
    }

    @Test
    public void testQueryForValidName() {
        final String name = "testqueryvalid.example.com";
        final Organization organization = TestBeanFactory.newOrganization();
        final models.cassandra.Host cassandraHost = TestBeanFactory.createCassandraHost(organization);
        cassandraHost.setName(name);

        final HostQuery query = _hostRepo.createHostQuery(organization);
        query.partialHostname(Optional.of(cassandraHost.getName()));
        assertEquals(0L, _hostRepo.queryHosts(query).total());

        final models.cassandra.Host.Mapper mapper = new models.cassandra.Host_MapperBuilder(_cassandraSession).build();
        final models.cassandra.Host.HostQueries dao = mapper.dao();
        dao.save(cassandraHost);

        final QueryResult<Host> result = _hostRepo.queryHosts(query);
        assertEquals(1L, result.total());
        assertHostCassandraEquivalent(result.values().get(0), cassandraHost);
    }

    @Test
    public void testGetHostCountWithNoHost() {
        assertEquals(0, _hostRepo.getHostCount(TestBeanFactory.newOrganization()));
    }

    @Test
    public void testGetHostCountWithMultipleHost() {
        final Organization org = TestBeanFactory.newOrganization();
        assertEquals(0, _hostRepo.getHostCount(org));
        final models.cassandra.Host.Mapper mapper = new models.cassandra.Host_MapperBuilder(_cassandraSession).build();
        final models.cassandra.Host.HostQueries dao = mapper.dao();

        final models.cassandra.Host cassandraHost1 = TestBeanFactory.createCassandraHost(org);
        dao.save(cassandraHost1);

        final models.cassandra.Host cassandraHost = TestBeanFactory.createCassandraHost(org);
        dao.save(cassandraHost);

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
    private CqlSession _cassandraSession;
}
