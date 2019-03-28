/**
 * Copyright 2018 Smartsheet
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

import com.arpnetworking.metrics.portal.alerts.impl.CassandraAlertRepository;
import com.arpnetworking.metrics.portal.hosts.HostRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.datastax.driver.core.Session;
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingManager;
import com.datastax.driver.mapping.Result;
import models.internal.Host;
import models.internal.HostQuery;
import models.internal.MetricsSoftwareState;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultHostQuery;
import models.internal.impl.DefaultQueryResult;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.inject.Inject;

/**
 * Implementation of {@link HostRepository} for Cassandra database.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class CassandraHostRepository implements HostRepository {
    /**
     * Public constructor.
     *
     * @param cassandraSession a Session to use to query data
     * @param mappingManager a MappingManager providing ORM for the Cassandra objects
     */
    @Inject
    public CassandraHostRepository(
            final Session cassandraSession,
            final MappingManager mappingManager) {
        _cassandraSession = cassandraSession;
        _mappingManager = mappingManager;
    }

    @Override
    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening alert repository").log();
        _isOpen.set(true);
    }

    @Override
    public void close() {
        assertIsOpen();
        LOGGER.debug().setMessage("Closing alert repository").log();
        _isOpen.set(false);
        _cassandraSession.close();
    }

    @Override
    public Optional<Host> getHost(final String hostname, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Getting host")
                .addData("hostname", hostname)
                .addData("organization", organization)
                .log();

        final Mapper<models.cassandra.Host> mapper = _mappingManager.mapper(models.cassandra.Host.class);
        final models.cassandra.Host cassandraHost = mapper.get(organization, hostname);

        if (cassandraHost == null) {
            return Optional.empty();
        }

        return Optional.of(cassandraHost.toInternal());
    }

    @Override
    public void addOrUpdateHost(final Host host, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Upserting host")
                .addData("host", host)
                .addData("organization", organization)
                .log();

        final models.cassandra.Host cassHost = new models.cassandra.Host();
        cassHost.setOrganization(organization.getId());
        cassHost.setCluster(host.getCluster().orElse(null));
        cassHost.setMetricsSoftwareState(host.getMetricsSoftwareState().name());
        cassHost.setName(host.getHostname());

        final Mapper<models.cassandra.Host> mapper = _mappingManager.mapper(models.cassandra.Host.class);
        mapper.save(cassHost);
    }

    @Override
    public void deleteHost(final String hostname, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Deleting host")
                .addData("hostname", hostname)
                .addData("organization", organization)
                .log();
        final Optional<Host> alert = getHost(hostname, organization);
        if (alert.isPresent()) {
            final Mapper<models.cassandra.Host> mapper = _mappingManager.mapper(models.cassandra.Host.class);
            mapper.delete(organization, hostname);
        }
    }

    @Override
    public HostQuery createQuery(final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Preparing query")
                .addData("organization", organization)
                .log();
        return new DefaultHostQuery(this, organization);
    }

    @Override
    public QueryResult<Host> query(final HostQuery query) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Querying")
                .addData("query", query)
                .log();
        final Mapper<models.cassandra.Host> mapper = _mappingManager.mapper(models.cassandra.Host.class);
        final models.cassandra.Host.HostQueries accessor = mapper.getManager().createAccessor(models.cassandra.Host.HostQueries.class);
        final Result<models.cassandra.Host> result = accessor.getHostsForOrganization(query.getOrganization().getId());
        final Spliterator<models.cassandra.Host> allAlerts = result.spliterator();
        final int start = query.getOffset().orElse(0);

        Stream<models.cassandra.Host> hostStream = StreamSupport.stream(allAlerts, false);

        if (query.getPartialHostname().isPresent()) {
            hostStream = hostStream
                    .filter(host -> {
                        final String partialHost = query.getPartialHostname().get().toLowerCase(Locale.ENGLISH);
                        return host.getName().toLowerCase(Locale.ENGLISH).contains(partialHost);
                    });
        }

        if (query.getCluster().isPresent()) {
            hostStream = hostStream
                    .filter(host -> {
                        final String cluster = query.getCluster().get().toLowerCase(Locale.ENGLISH);
                        return host.getCluster().toLowerCase(Locale.ENGLISH).equals(cluster);
                    });
        }

        if (query.getMetricsSoftwareState().isPresent()) {
            hostStream = hostStream
                    .filter(host -> {
                        final String cluster = query.getCluster().get().toLowerCase(Locale.ENGLISH);
                        return host.getCluster().toLowerCase(Locale.ENGLISH).equals(cluster);
                    });
        }

        final Comparator<Host> sort;
        if (query.getSortBy().isPresent()) {
            final HostQuery.Field field = query.getSortBy().get();
            if (field == HostQuery.Field.HOSTNAME) {
                sort = Comparator.comparing(Host::getHostname);
            } else if (field.equals(HostQuery.Field.METRICS_SOFTWARE_STATE)) {
                sort = Comparator.comparing(Host::getMetricsSoftwareState);
            } else {
                throw new RuntimeException("Sort field not supported: " + field);
            }
        } else {
            sort = Comparator.comparing(Host::getHostname);
        }

        final List<Host> hosts = hostStream
                .map(models.cassandra.Host::toInternal)
                .sorted(sort)
                .collect(Collectors.toList());
        final List<Host> paginated = hosts.stream().skip(start).limit(query.getLimit()).collect(Collectors.toList());
        return new DefaultQueryResult<>(paginated, hosts.size());
    }

    @Override
    public long getHostCount(final Organization organization) {
        final Mapper<models.cassandra.Host> mapper = _mappingManager.mapper(models.cassandra.Host.class);
        final models.cassandra.Host.HostQueries accessor = mapper.getManager().createAccessor(models.cassandra.Host.HostQueries.class);
        final Result<models.cassandra.Host> result = accessor.getHostsForOrganization(organization.getId());
        return StreamSupport.stream(result.spliterator(), false).count();
    }

    @Override
    public long getHostCount(final MetricsSoftwareState metricsSoftwareState, final Organization organization) {
        final Mapper<models.cassandra.Host> mapper = _mappingManager.mapper(models.cassandra.Host.class);
        final models.cassandra.Host.HostQueries accessor = mapper.getManager().createAccessor(models.cassandra.Host.HostQueries.class);
        final Result<models.cassandra.Host> result = accessor.getHostsForOrganization(organization.getId());
        return StreamSupport.stream(result.spliterator(), false)
                .filter(host -> metricsSoftwareState.name().equals(host.getMetricsSoftwareState()))
                .count();
    }

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_isOpen.get() != expectedState) {
            throw new IllegalStateException(String.format("Alert repository is not %s", expectedState ? "open" : "closed"));
        }
    }

    private final Session _cassandraSession;
    private final MappingManager _mappingManager;
    private final AtomicBoolean _isOpen = new AtomicBoolean(false);

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraAlertRepository.class);
}
