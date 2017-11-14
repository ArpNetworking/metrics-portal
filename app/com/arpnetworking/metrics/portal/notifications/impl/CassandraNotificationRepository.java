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
package com.arpnetworking.metrics.portal.notifications.impl;

import com.arpnetworking.metrics.portal.alerts.impl.CassandraAlertRepository;
import com.arpnetworking.metrics.portal.notifications.NotificationRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.datastax.driver.core.Session;
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingManager;
import com.datastax.driver.mapping.Result;
import com.google.inject.Inject;
import models.cassandra.NotificationRecipient;
import models.internal.NotificationEntry;
import models.internal.NotificationGroup;
import models.internal.NotificationGroupQuery;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultNotificationGroupQuery;
import models.internal.impl.DefaultQueryResult;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Spliterator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A notification repository backed by cassandra.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class CassandraNotificationRepository implements NotificationRepository {
    /**
     * Public constructor.
     *
     * @param cassandraSession a Session to use to query data
     * @param mappingManager a MappingManager providing ORM for the Cassandra objects
     */
    @Inject
    public CassandraNotificationRepository(final Session cassandraSession, final MappingManager mappingManager) {
        _cassandraSession = cassandraSession;
        _mappingManager = mappingManager;
    }

    @Override
    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening notification repository").log();
        _isOpen.set(true);
    }

    @Override
    public void close() {
        assertIsOpen();
        LOGGER.debug().setMessage("Closing notification repository").log();
        _isOpen.set(false);
        _cassandraSession.close();
    }

    @Override
    public Optional<NotificationGroup> getNotificationGroup(final UUID identifier, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Getting notification group")
                .addData("notificationGroupId", identifier)
                .addData("organization", organization)
                .log();
        final Mapper<models.cassandra.NotificationGroup> mapper = _mappingManager.mapper(models.cassandra.NotificationGroup.class);
        final models.cassandra.NotificationGroup cassandraNotificationGroup = mapper.get(identifier);

        if (cassandraNotificationGroup == null) {
            return Optional.empty();
        }

        return Optional.of(cassandraNotificationGroup.toInternal());
    }

    @Override
    public NotificationGroupQuery createQuery(final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Preparing query")
                .addData("organization", organization)
                .log();
        return new DefaultNotificationGroupQuery(this, organization);
    }

    @Override
    public QueryResult<NotificationGroup> query(final NotificationGroupQuery query) {
        final Mapper<models.cassandra.NotificationGroup> mapper = _mappingManager.mapper(models.cassandra.NotificationGroup.class);
        final models.cassandra.NotificationGroup.NotificationGroupQueries accessor =
                mapper.getManager().createAccessor(models.cassandra.NotificationGroup.NotificationGroupQueries.class);
        final Result<models.cassandra.NotificationGroup> result =
                accessor.getNotificationGroupsForOrganization(query.getOrganization().getId());
        final Spliterator<models.cassandra.NotificationGroup> allNotificationGroups = result.spliterator();
        final int start = query.getOffset().orElse(0);

        Stream<models.cassandra.NotificationGroup> notificationGroupStream = StreamSupport.stream(allNotificationGroups, false);

        if (query.getContains().isPresent()) {
            notificationGroupStream = notificationGroupStream.filter(notificationGroup -> {
                final String contains = query.getContains().get().toLowerCase(Locale.ENGLISH);
                return notificationGroup.getName().toLowerCase(Locale.ENGLISH).contains(contains);
            });
        }

        final List<NotificationGroup> notificationGroups = notificationGroupStream
                .map(models.cassandra.NotificationGroup::toInternal)
                .collect(Collectors.toList());
        final List<NotificationGroup> paginated = notificationGroups.stream()
                .skip(start)
                .limit(query.getLimit())
                .collect(Collectors.toList());
        return new DefaultQueryResult<>(paginated, notificationGroups.size());
    }

    @Override
    public void addOrUpdateNotificationGroup(final NotificationGroup group, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Creating notification group")
                .addData("notificationGroiup", group)
                .addData("organization", organization)
                .log();

        final Mapper<models.cassandra.NotificationGroup> mapper = _mappingManager.mapper(models.cassandra.NotificationGroup.class);
        models.cassandra.NotificationGroup cassandraNotificationGroup = mapper.get(group.getId());

        if (cassandraNotificationGroup == null) {
            cassandraNotificationGroup = new models.cassandra.NotificationGroup();
        }

        cassandraNotificationGroup.setUuid(group.getId());
        cassandraNotificationGroup.setOrganization(organization.getId());
        cassandraNotificationGroup.setName(group.getName());

        mapper.save(cassandraNotificationGroup);
    }

    @Override
    public void addRecipientToNotificationGroup(
            final NotificationGroup group,
            final Organization organization,
            final NotificationEntry recipient) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Adding recipient to notification group")
                .addData("notificationGroup", group)
                .addData("organization", organization)
                .addData("recipient", recipient)
                .log();

        final Optional<NotificationGroup> notificationGroupOptional = getNotificationGroup(group.getId(), organization);
        notificationGroupOptional.ifPresent(internalGroup -> {
            final models.cassandra.NotificationGroup notificationGroup =
                    models.cassandra.NotificationGroup.fromInternal(internalGroup, organization);
            final NotificationRecipient toAdd = NotificationRecipient.fromInternal(recipient);
            if (notificationGroup.getRecipients().contains(toAdd)) {
                return;
            }
            notificationGroup.getRecipients().add(toAdd);
            _mappingManager.mapper(models.cassandra.NotificationGroup.class).save(notificationGroup);
        });
    }

    @Override
    public void removeRecipientFromNotificationGroup(
            final NotificationGroup group,
            final Organization organization,
            final NotificationEntry recipient) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Removing recipient from notification group")
                .addData("notificationGroup", group)
                .addData("organization", organization)
                .addData("recipient", recipient)
                .log();

        final Optional<NotificationGroup> notificationGroupOptional = getNotificationGroup(group.getId(), organization);
        notificationGroupOptional.ifPresent(internalGroup -> {
            final models.cassandra.NotificationGroup notificationGroup =
                    models.cassandra.NotificationGroup.fromInternal(internalGroup, organization);
            if (!notificationGroup.getRecipients().remove(NotificationRecipient.fromInternal(recipient))) {
                LOGGER.debug()
                        .setMessage("Recipient not in notification group")
                        .addData("group", group)
                        .addData("recipient", recipient)
                        .addData("organization", organization)
                        .log();
                return;
            }
            _mappingManager.mapper(models.cassandra.NotificationGroup.class).save(notificationGroup);
        });

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
