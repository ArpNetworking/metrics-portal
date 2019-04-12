/*
 * Copyright 2014 Groupon.com
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

import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import models.internal.Host;
import models.internal.HostQuery;
import models.internal.MetricsSoftwareState;
import models.internal.QueryResult;
import models.internal.impl.DefaultHost;
import org.apache.commons.io.FileUtils;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for <code>ElasticSearchHostRegistry</code>.
 *
 * @author Ruchita Venugopal (rvenugopal at groupon dot com)
 */
public final class ElasticSearchHostRegistryTest {

    @Before
    public void before() {
        _tempDirectory = Files.createTempDir();
        try {
            FileUtils.deleteDirectory(_tempDirectory);
        } catch (final IOException ioe) {
            // Do nothing
        }
        _repository = new ElasticSearchHostRepository(
                ImmutableSettings.settingsBuilder()
                        .put("cluster.name", "ElasticSearchHostRegistryTest")
                        .put("node.local", "true")
                        .put("node.data", "true")
                        .put("path.logs", _tempDirectory.getAbsolutePath() + "/logs")
                        .put("path.data", _tempDirectory.getAbsolutePath() + "/data")
                        .build(),
                ImmutableSettings.settingsBuilder()
                        .put("number_of_shards", "1")
                        .put("number_of_replicas", "0")
                        .put("refresh_interval", "1s")
                        .build());
        _repository.open();
    }

    @After
    public void tearDown() {
        _repository.close();
        try {
            FileUtils.deleteDirectory(_tempDirectory);
        } catch (final IOException ioe) {
            // Do nothing
        }
    }

    @Test
    public void testAddHost() throws InterruptedException {
        final Host expectedHost = addOrUpdateHost("testAddHost-host1", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);

        // Indexing is asynchronous at an interval of 1 second (see @Before)
        Thread.sleep(2000);

        final HostQuery hostQuery = _repository.createQuery(TestBeanFactory.getDefautOrganization())
                .partialHostname(Optional.of(expectedHost.getHostname()));
        final QueryResult<Host> result = _repository.query(hostQuery);
        final Host actualHost = Iterables.getFirst(result.values(), null);
        assertEquals(expectedHost, actualHost);
        assertEquals(1, result.total());
    }

    @Test
    public void testUpdateHost() throws InterruptedException {
        addOrUpdateHost("testUpdateHost-host1", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);
        final Host expectedHost = addOrUpdateHost("testUpdateHost-host1", MetricsSoftwareState.OLD_VERSION_INSTALLED, null);

        // Indexing is asynchronous at an interval of 1 second (see @Before)
        Thread.sleep(2000);

        final HostQuery hostQuery = _repository.createQuery(TestBeanFactory.getDefautOrganization())
                .partialHostname(Optional.of(expectedHost.getHostname()));
        final QueryResult<Host> result = _repository.query(hostQuery);
        final Host actualHost = Iterables.getFirst(result.values(), null);
        assertEquals(expectedHost, actualHost);
        assertEquals(1, result.total());
    }

    @Test
    public void testDeleteHost() throws InterruptedException {
        final Host deletedHost = addOrUpdateHost("testDeleteHost-host1", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);
        _repository.deleteHost(deletedHost.getHostname(), TestBeanFactory.getDefautOrganization());

        // Indexing is asynchronous at an interval of 1 second (see @Before)
        Thread.sleep(2000);

        final HostQuery hostQuery = _repository.createQuery(TestBeanFactory.getDefautOrganization())
                .partialHostname(Optional.of(deletedHost.getHostname()));
        final QueryResult<Host> result = _repository.query(hostQuery);
        assertTrue(result.values().isEmpty());
        assertEquals(0, result.total());
    }

    @Test
    public void testFindAllHosts() throws InterruptedException {
        final Host expectedHost1 = addOrUpdateHost("testFindAllHostsA", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);
        final Host expectedHost2 = addOrUpdateHost("testFindAllHostsB", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);
        final Host expectedHost3 = addOrUpdateHost("testFindAllHostsC", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);
        final Host expectedHost4 = addOrUpdateHost("testFindAllHostsD", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);

        // Indexing is asynchronous at an interval of 1 second (see @Before)
        Thread.sleep(2000);

        final HostQuery hostQuery = _repository.createQuery(TestBeanFactory.getDefautOrganization()).limit(10);
        final QueryResult<Host> result = _repository.query(hostQuery);
        final List<? extends Host> hosts = result.values();
        assertEquals(4, hosts.size());
        assertTrue(hosts.contains(expectedHost1));
        assertTrue(hosts.contains(expectedHost2));
        assertTrue(hosts.contains(expectedHost3));
        assertTrue(hosts.contains(expectedHost4));
    }

    @Test
    public void testFindHostsWithName() throws InterruptedException {
        final Host expectedHost = addOrUpdateHost("testFindHostsWithName-host1", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);
        addOrUpdateHost("host-foo", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);

        // Indexing is asynchronous at an interval of 1 second (see @Before)
        Thread.sleep(2000);

        final HostQuery hostQuery = _repository.createQuery(TestBeanFactory.getDefautOrganization())
                .partialHostname(Optional.of("testFindHostsWithName-host1"));
        final QueryResult<Host> result = _repository.query(hostQuery);
        final List<? extends Host> hosts = result.values();
        assertEquals(1, hosts.size());
        assertEquals(expectedHost, Iterables.getFirst(hosts, null));
    }

    @Test
    public void testFindHostsWithCluster() throws InterruptedException {
        final Host expectedHost = addOrUpdateHost(
                "testFindHostsWithName-host1",
                MetricsSoftwareState.LATEST_VERSION_INSTALLED,
                "cluster1");
        addOrUpdateHost("host-foo", MetricsSoftwareState.LATEST_VERSION_INSTALLED, "cluster2");

        // Indexing is asynchronous at an interval of 1 second (see @Before)
        Thread.sleep(2000);

        final HostQuery query = _repository.createQuery(TestBeanFactory.getDefautOrganization()).cluster(Optional.of("cluster1"));
        final QueryResult<Host> result = _repository.query(query);
        final List<? extends Host> hosts = result.values();
        assertEquals(1, hosts.size());
        assertEquals(expectedHost, Iterables.getFirst(hosts, null));
    }

    @Test
    public void testFindHostsWithNamePrefix() throws InterruptedException {
        final Host expectedHost1 = addOrUpdateHost(
                "testFindHostsWithNamePrefix-Foo",
                MetricsSoftwareState.LATEST_VERSION_INSTALLED,
                null);
        final Host expectedHost2 = addOrUpdateHost(
                "testFindHostsWithNamePrefix-Bar",
                MetricsSoftwareState.LATEST_VERSION_INSTALLED,
                null);
        final Host expectedHost3 = addOrUpdateHost(
                "hostfoo",
                MetricsSoftwareState.LATEST_VERSION_INSTALLED,
                null);
        final Host expectedHost4 = addOrUpdateHost(
                "hostbar",
                MetricsSoftwareState.LATEST_VERSION_INSTALLED,
                null);

        // Indexing is asynchronous at an interval of 1 second (see @Before)
        Thread.sleep(2000);

        final HostQuery hostQuery1 = _repository.createQuery(TestBeanFactory.getDefautOrganization())
                .partialHostname(Optional.of("testFindHostsWithNamePrefix"));
        final QueryResult<Host> result1 = _repository.query(hostQuery1);
        final List<? extends Host> hosts1 = result1.values();
        assertEquals(2, hosts1.size());
        assertTrue(hosts1.contains(expectedHost1));
        assertTrue(hosts1.contains(expectedHost2));

        final HostQuery hostQuery2 = _repository.createQuery(TestBeanFactory.getDefautOrganization())
                .partialHostname(Optional.of("host"));
        final QueryResult<Host> result2 = _repository.query(hostQuery2);
        final List<? extends Host> hosts2 = result2.values();
        assertEquals(2, hosts2.size());
        assertTrue(hosts2.contains(expectedHost3));
        assertTrue(hosts2.contains(expectedHost4));
    }

    @Test
    public void testFindHostsWithNameAndState() throws InterruptedException {
        addOrUpdateHost(
                "testFindHostsWithNameAndState-host1",
                MetricsSoftwareState.LATEST_VERSION_INSTALLED,
                null);
        final Host expectedHost = addOrUpdateHost(
                "testFindHostsWithNameAndState-host2",
                MetricsSoftwareState.OLD_VERSION_INSTALLED,
                null);
        addOrUpdateHost("host-foo", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);
        addOrUpdateHost("host-bar", MetricsSoftwareState.OLD_VERSION_INSTALLED, null);

        // Indexing is asynchronous at an interval of 1 second (see @Before)
        Thread.sleep(2000);

        final HostQuery hostQuery = _repository.createQuery(TestBeanFactory.getDefautOrganization())
                .partialHostname(Optional.of("testFindHostsWithNameAndState"))
                .metricsSoftwareState(Optional.of(MetricsSoftwareState.OLD_VERSION_INSTALLED));
        final QueryResult<Host> result = _repository.query(hostQuery);
        final List<? extends Host> hosts = result.values();
        assertEquals(1, hosts.size());
        assertTrue(hosts.contains(expectedHost));
    }

    @Test
    public void testFindHostsWithNameWithLimit() throws InterruptedException {
        addOrUpdateHost("testFindHostsWithNameWithLimit-host1", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);
        addOrUpdateHost("testFindHostsWithNameWithLimit-host2", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);
        addOrUpdateHost("testFindHostsWithNameWithLimit-host3", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);
        addOrUpdateHost("testFindHostsWithNameWithLimit-host4", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);

        // Indexing is asynchronous at an interval of 1 second (see @Before)
        Thread.sleep(2000);

        final HostQuery hostQuery = _repository.createQuery(TestBeanFactory.getDefautOrganization())
                .partialHostname(Optional.of("testFindHostsWithNameWithLimit"))
                .limit(1);
        final QueryResult<Host> result = _repository.query(hostQuery);
        final List<? extends Host> hosts = result.values();
        assertEquals(1, hosts.size());
    }

    @Test
    public void testFindHostsSortByScoreDefault() throws InterruptedException {
        final Host expectedHost1 = addOrUpdateHost("abc-host", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);
        final Host expectedHost2 = addOrUpdateHost("host", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);

        // Indexing is asynchronous at an interval of 1 second (see @Before)
        Thread.sleep(2000);

        final HostQuery hostQuery = _repository.createQuery(TestBeanFactory.getDefautOrganization())
                .partialHostname(Optional.of("host"));
        final QueryResult<Host> result = _repository.query(hostQuery);
        final List<? extends Host> hosts = result.values();
        assertEquals(2, hosts.size());
        assertEquals(expectedHost2, hosts.get(0));
        assertEquals(expectedHost1, hosts.get(1));
    }

    @Test
    public void testFindHostsSortByHostname() throws InterruptedException {
        final Host expectedHost1 = addOrUpdateHost("abc-host", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);
        final Host expectedHost2 = addOrUpdateHost("host-def", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);

        // Indexing is asynchronous at an interval of 1 second (see @Before)
        Thread.sleep(2000);

        final HostQuery hostQuery = _repository.createQuery(TestBeanFactory.getDefautOrganization())
                .partialHostname(Optional.of("host"))
                .sortBy(Optional.of(HostQuery.Field.HOSTNAME));
        final QueryResult<Host> result = _repository.query(hostQuery);
        final List<? extends Host> hosts = result.values();
        assertEquals(2, hosts.size());
        assertEquals(expectedHost1, hosts.get(0));
        assertEquals(expectedHost2, hosts.get(1));
    }

    @Test
    public void testFindHostsOffset() throws InterruptedException {
        addOrUpdateHost("a-host", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);
        addOrUpdateHost("b-host", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);
        final Host expectedHost1 = addOrUpdateHost("c-host", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);
        final Host expectedHost2 = addOrUpdateHost("d-host", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);
        final Host expectedHost3 = addOrUpdateHost("e-host", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);

        // Indexing is asynchronous at an interval of 1 second (see @Before)
        Thread.sleep(2000);

        final HostQuery hostQuery = _repository.createQuery(TestBeanFactory.getDefautOrganization())
                .partialHostname(Optional.of("host"))
                .offset(Optional.of(2))
                .sortBy(Optional.of(HostQuery.Field.HOSTNAME));
        final QueryResult<Host> result = _repository.query(hostQuery);
        final List<? extends Host> hosts = result.values();
        assertEquals(3, hosts.size());
        assertEquals(expectedHost1, hosts.get(0));
        assertEquals(expectedHost2, hosts.get(1));
        assertEquals(expectedHost3, hosts.get(2));
        assertEquals(5, result.total());
    }

    @Test
    public void testCountHosts() throws InterruptedException {
        addOrUpdateHost("testCountHosts-host1", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);
        addOrUpdateHost("testCountHosts-host2", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);
        addOrUpdateHost("testCountHosts-host3", MetricsSoftwareState.OLD_VERSION_INSTALLED, null);
        addOrUpdateHost("testCountHosts-host4", MetricsSoftwareState.LATEST_VERSION_INSTALLED, null);

        // Indexing is asynchronous at an interval of 1 second (see @Before)
        Thread.sleep(2000);

        assertEquals(4, _repository.getHostCount(TestBeanFactory.getDefautOrganization()));
        assertEquals(3, _repository.getHostCount(MetricsSoftwareState.LATEST_VERSION_INSTALLED, TestBeanFactory.getDefautOrganization()));
        assertEquals(1, _repository.getHostCount(MetricsSoftwareState.OLD_VERSION_INSTALLED, TestBeanFactory.getDefautOrganization()));
        assertEquals(0, _repository.getHostCount(MetricsSoftwareState.NOT_INSTALLED, TestBeanFactory.getDefautOrganization()));
    }

    private Host addOrUpdateHost(final String name, final MetricsSoftwareState state, @Nullable final String cluster) {
        final Host host = new DefaultHost.Builder()
                .setHostname(name)
                .setMetricsSoftwareState(state)
                .setCluster(cluster)
                .build();
        _repository.addOrUpdateHost(host, TestBeanFactory.getDefautOrganization());
        return host;
    }

    private ElasticSearchHostRepository _repository;
    private File _tempDirectory;
}
