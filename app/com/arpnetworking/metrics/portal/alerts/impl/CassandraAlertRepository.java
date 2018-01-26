/**
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

import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.metrics.portal.notifications.NotificationRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.datastax.driver.core.Session;
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingManager;
import com.datastax.driver.mapping.Result;
import com.google.common.collect.ImmutableMap;
import models.internal.Alert;
import models.internal.AlertQuery;
import models.internal.NagiosExtension;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultAlertQuery;
import models.internal.impl.DefaultQueryResult;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.inject.Inject;

/**
 * Implementation of an {@link AlertRepository} that stores the data in Cassandra.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class CassandraAlertRepository implements AlertRepository {
    /**
     * Public constructor.
     *
     * @param cassandraSession a Session to use to query data
     * @param mappingManager a MappingManager providing ORM for the Cassandra objects
     * @param notificationRepository Notification repository used to create and lookup notification groups
     */
    @Inject
    public CassandraAlertRepository(
            final Session cassandraSession,
            final MappingManager mappingManager,
            final NotificationRepository notificationRepository) {
        _cassandraSession = cassandraSession;
        _mappingManager = mappingManager;
        _notificationRepository = notificationRepository;
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
    public Optional<Alert> get(final UUID identifier, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Getting alert")
                .addData("alertId", identifier)
                .addData("organization", organization)
                .log();
        final Mapper<models.cassandra.Alert> mapper = _mappingManager.mapper(models.cassandra.Alert.class);
        final models.cassandra.Alert cassandraAlert = mapper.get(identifier);

        if (cassandraAlert == null) {
            return Optional.empty();
        }

        return Optional.of(cassandraAlert.toInternal(_notificationRepository));
    }

    @Override
    public int delete(final UUID identifier, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Deleting alert")
                .addData("alertId", identifier)
                .addData("organization", organization)
                .log();
        final Optional<Alert> alert = get(identifier, organization);
        if (alert.isPresent()) {
            final Mapper<models.cassandra.Alert> mapper = _mappingManager.mapper(models.cassandra.Alert.class);
            mapper.delete(identifier);
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public AlertQuery createQuery(final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Preparing query")
                .addData("organization", organization)
                .log();
        return new DefaultAlertQuery(this, organization);
    }

    @Override
    public QueryResult<Alert> query(final AlertQuery query) {
        final Mapper<models.cassandra.Alert> mapper = _mappingManager.mapper(models.cassandra.Alert.class);
        final models.cassandra.Alert.AlertQueries accessor = mapper.getManager().createAccessor(models.cassandra.Alert.AlertQueries.class);
        final Result<models.cassandra.Alert> result = accessor.getAlertsForOrganization(query.getOrganization().getId());
        final Spliterator<models.cassandra.Alert> allAlerts = result.spliterator();
        final int start = query.getOffset().orElse(0);

        Stream<models.cassandra.Alert> alertStream = StreamSupport.stream(allAlerts, false);

        if (query.getContains().isPresent()) {
            alertStream = alertStream.filter(alert -> {
                final String contains = query.getContains().get().toLowerCase(Locale.ENGLISH);
                return alert.getQuery().toLowerCase(Locale.ENGLISH).contains(contains)
                        || alert.getName().toLowerCase(Locale.ENGLISH).contains(contains);
            });
        }

        final List<Alert> alerts = alertStream
                .map(alert -> alert.toInternal(_notificationRepository))
                .collect(Collectors.toList());
        final List<Alert> paginated = alerts.stream().skip(start).limit(query.getLimit()).collect(Collectors.toList());
        return new DefaultQueryResult<>(paginated, alerts.size());
    }

    @Override
    public long getAlertCount(final Organization organization) {
        final Mapper<models.cassandra.Alert> mapper = _mappingManager.mapper(models.cassandra.Alert.class);
        final models.cassandra.Alert.AlertQueries accessor = mapper.getManager().createAccessor(models.cassandra.Alert.AlertQueries.class);
        final Result<models.cassandra.Alert> result = accessor.getAlertsForOrganization(organization.getId());
        return result.all().stream().count();
    }

    @Override
    public void addOrUpdateAlert(final Alert alert, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Upserting alert")
                .addData("alert", alert)
                .addData("organization", organization)
                .log();

        final models.cassandra.Alert cassAlert = new models.cassandra.Alert();
        cassAlert.setUuid(alert.getId());
        cassAlert.setOrganization(organization.getId());
        cassAlert.setNagiosExtensions(convertToCassandraNagiosExtension(alert.getNagiosExtension()));
        cassAlert.setName(alert.getName());
        cassAlert.setQuery(alert.getQuery());
        cassAlert.setComment(alert.getComment());
        if (alert.getNotificationGroup() != null) {
            cassAlert.setNotificationGroupId(alert.getNotificationGroup().getId());
        } else {
            cassAlert.setNotificationGroupId(null);
        }
        cassAlert.setPeriodInSeconds(alert.getPeriod().toStandardSeconds().getSeconds());

        final Mapper<models.cassandra.Alert> mapper = _mappingManager.mapper(models.cassandra.Alert.class);
        mapper.save(cassAlert);
    }

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_isOpen.get() != expectedState) {
            throw new IllegalStateException(String.format("Alert repository is not %s", expectedState ? "open" : "closed"));
        }
    }

    private Map<String, String> convertToCassandraNagiosExtension(final NagiosExtension nagiosExtension) {
        final ImmutableMap.Builder<String, String> nagiosMapBuilder = new ImmutableMap.Builder<>();
        if (nagiosExtension != null) {
            nagiosMapBuilder.put("severity", nagiosExtension.getSeverity());
            nagiosMapBuilder.put("notify", nagiosExtension.getNotify());
            nagiosMapBuilder.put("attempts", Integer.toString(nagiosExtension.getMaxCheckAttempts()));
            nagiosMapBuilder.put("freshness", Long.toString(nagiosExtension.getFreshnessThreshold().getStandardSeconds()));
        }
        return nagiosMapBuilder.build();
    }

    private final Session _cassandraSession;
    private final MappingManager _mappingManager;
    private final AtomicBoolean _isOpen = new AtomicBoolean(false);
    private final NotificationRepository _notificationRepository;

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraAlertRepository.class);
}
