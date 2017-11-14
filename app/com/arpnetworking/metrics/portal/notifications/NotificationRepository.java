/**
 * Copyright 2017 Smartsheet
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
package com.arpnetworking.metrics.portal.notifications;

import models.internal.NotificationEntry;
import models.internal.NotificationGroup;
import models.internal.NotificationGroupQuery;
import models.internal.Organization;
import models.internal.QueryResult;

import java.util.Optional;
import java.util.UUID;

/**
 * A repository to store notification groups.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public interface NotificationRepository {
    /**
     * Open the {@link NotificationRepository}.
     */
    void open();

    /**
     * Close the {@link NotificationRepository}.
     */
    void close();

    /**
     * Get the {@link NotificationGroup} by identifier.
     *
     * @param identifier The {@link NotificationGroup} identifier.
     * @param organization The organization owning the group.
     * @return The matching {@link NotificationGroup} if found or <code>Optional.empty()</code>.
     */
    Optional<NotificationGroup> getNotificationGroup(UUID identifier, Organization organization);

    /**
     * Create a query against the notification group repository.
     *
     * @param organization Organization to search in.
     * @return Instance of {@link NotificationGroupQuery}.
     */
    NotificationGroupQuery createQuery(Organization organization);

    /**
     * Query notification groups.
     *
     * @param query Instance of {@link NotificationGroupQuery}.
     * @return The {@link QueryResult} of all notification groups matching the query.
     */
    QueryResult<NotificationGroup> query(NotificationGroupQuery query);

    /**
     * Add a new notification group or update an existing one in the repository.
     *
     * @param group The notification group to add to the repository.
     * @param organization The organization owning the group.
     */
    void addOrUpdateNotificationGroup(NotificationGroup group, Organization organization);

    /**
     * Add a new recipient to a notification group.
     *
     * @param group The notification group to add to the repository.
     * @param organization The organization owning the group.
     * @param recipient The recipient
     */
    void addRecipientToNotificationGroup(NotificationGroup group, Organization organization, NotificationEntry recipient);

    /**
     * Remove a recipient from a notification group.
     *
     * @param group The notification group to add to the repository.
     * @param organization The organization owning the group.
     * @param recipient The recipient
     */
    void removeRecipientFromNotificationGroup(NotificationGroup group, Organization organization, NotificationEntry recipient);
}
