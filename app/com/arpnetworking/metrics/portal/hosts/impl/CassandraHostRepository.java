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

import com.arpnetworking.metrics.portal.hosts.HostRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.datastax.oss.driver.api.core.CqlSession;
import models.internal.Host;
import models.internal.HostQuery;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultHostQuery;
import models.internal.impl.DefaultQueryResult;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Spliterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jakarta.inject.Inject;

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
     */
    @Inject
    public CassandraHostRepository(
            final CqlSession cassandraSession) {
        _cassandraSession = cassandraSession;
    }

    @Override
    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening host repository").log();
        _isOpen.set(true);
    }

    @Override
    public void close() {
        assertIsOpen();
        LOGGER.debug().setMessage("Closing host repository").log();
        _isOpen.set(false);
    }

    @Override
    public Optional<Host> getHost(final String hostname, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Getting host")
                .addData("hostname", hostname)
                .addData("organization", organization)
                .log();

        final models.cassandra.Host.Mapper mapper = new models.cassandra.Host_MapperBuilder(_cassandraSession).build();
        final models.cassandra.Host.HostQueries dao = mapper.dao();
        try {
            return  dao.get(organization.getId(), hostname).thenApply(cassandraHost -> {
                if (cassandraHost == null) {
                    return Optional.<Host>empty();
                }
                return Optional.of(cassandraHost.toInternal());
            }).toCompletableFuture().get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

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

        final models.cassandra.Host.Mapper mapper = new models.cassandra.Host_MapperBuilder(_cassandraSession).build();
        final models.cassandra.Host.HostQueries dao = mapper.dao();
        dao.save(cassHost);
    }

    @Override
    public void deleteHost(final String hostname, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Deleting host")
                .addData("hostname", hostname)
                .addData("organization", organization)
                .log();
        final Optional<Host> host = getHost(hostname, organization);
        if (host.isPresent()) {
            final models.cassandra.Host.Mapper mapper = new models.cassandra.Host_MapperBuilder(_cassandraSession).build();
            final models.cassandra.Host.HostQueries dao = mapper.dao();
            dao.delete(organization.getId(), hostname);
        }
    }

    @Override
    public HostQuery createHostQuery(final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Preparing query")
                .addData("organization", organization)
                .log();
        return new DefaultHostQuery(this, organization);
    }

    @Override
    public QueryResult<Host> queryHosts(final HostQuery query) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Querying")
                .addData("query", query)
                .log();
        final models.cassandra.Host.Mapper mapper = new models.cassandra.Host_MapperBuilder(_cassandraSession).build();
        final models.cassandra.Host.HostQueries dao = mapper.dao();
        final Stream<models.cassandra.Host> result = dao.getHostsForOrganization(query.getOrganization().getId());
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
        final models.cassandra.Host.Mapper mapper = new models.cassandra.Host_MapperBuilder(_cassandraSession)
                .withDefaultKeyspace("portal")
                .build();
        final models.cassandra.Host.HostQueries dao = mapper.dao();
        final Stream<models.cassandra.Host> result = dao.getHostsForOrganization(organization.getId());
        return result.count();
    }

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_isOpen.get() != expectedState) {
            throw new IllegalStateException(String.format("Alert repository is not %s", expectedState ? "open" : "closed"));
        }
    }

    private final CqlSession _cassandraSession;
    private final AtomicBoolean _isOpen = new AtomicBoolean(false);

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraHostRepository.class);
}
