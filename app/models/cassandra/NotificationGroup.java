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
package models.cassandra;

import com.datastax.driver.mapping.Result;
import com.datastax.driver.mapping.annotations.Accessor;
import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.Param;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Query;
import com.datastax.driver.mapping.annotations.Table;
import models.internal.Organization;
import models.internal.impl.DefaultNotificationGroup;
import org.joda.time.Instant;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.persistence.Version;

/**
 * Model for alerts stored in Cassandra.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Table(name = "notification_groups", keyspace = "portal")
public class NotificationGroup {
    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "uuid")
    @PartitionKey
    private UUID uuid;

    @Column(name = "name")
    private String name;

    @Column(name = "organization")
    private UUID organization;

    @Column(name = "recipients")
    private List<NotificationRecipient> recipients = Collections.emptyList();

    public Long getVersion() {
        return version;
    }

    public void setVersion(final Long value) {
        version = value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant value) {
        createdAt = value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final Instant value) {
        updatedAt = value;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(final UUID value) {
        uuid = value;
    }

    public String getName() {
        return name;
    }

    public void setName(final String value) {
        name = value;
    }

    public UUID getOrganization() {
        return organization;
    }

    public void setOrganization(final UUID value) {
        organization = value;
    }

    public List<NotificationRecipient> getRecipients() {
        return recipients;
    }

    public void setRecipients(final List<NotificationRecipient> value) {
        recipients = value;
    }

    /**
     * Creates a {@link models.internal.NotificationGroup} from this instance.
     *
     * @return a new {@link models.internal.NotificationGroup}
     */
    public models.internal.NotificationGroup toInternal() {
        return new DefaultNotificationGroup.Builder()
                .setEntries(getRecipients().stream().map(NotificationRecipient::toInternal).collect(Collectors.toList()))
                .setId(getUuid())
                .setName(getName())
                .build();
    }

    /**
     * Creates a {@link NotificationGroup} from a {@link models.internal.NotificationGroup}.
     * @param internalGroup a notification group
     * @param organization the organization associated with the group
     * @return a new {@link NotificationGroup}
     */
    public static NotificationGroup fromInternal(final models.internal.NotificationGroup internalGroup, final Organization organization) {
        final NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setName(internalGroup.getName());
        notificationGroup.setOrganization(organization.getId());
        notificationGroup.setUuid(internalGroup.getId());
        notificationGroup.setRecipients(
                internalGroup.getEntries()
                        .stream()
                        .map(NotificationRecipient::fromInternal)
                        .collect(Collectors.toList()));
        return notificationGroup;
    }

    /**
     * Queries for alerts.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    @Accessor
    public interface NotificationGroupQueries {
        /**
         * Queries for all notification groups in an organization.
         *
         * @param organization Organization owning the notification groups
         * @return Mapped query results
         */
        @Query("select * from portal.notification_groups_by_organization where organization = :org")
        Result<NotificationGroup> getNotificationGroupsForOrganization(@Param("org") UUID organization);
    }
}
// CHECKSTYLE.ON: MemberNameCheck
