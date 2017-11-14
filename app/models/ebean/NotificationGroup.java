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
package models.ebean;


import io.ebean.Finder;
import io.ebean.annotation.PrivateOwned;
import models.internal.impl.DefaultNotificationGroup;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

// CHECKSTYLE.OFF: MemberNameCheck

/**
 * Represents a notification group in the database.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
@Entity
@Table(name = "notification_groups", schema = "portal")
public class NotificationGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "uuid")
    private UUID uuid;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL)
    @PrivateOwned
    private List<NotificationRecipient> recipients;

    @ManyToOne(optional = false)
    @JoinColumn(name = "organization")
    private Organization organization;

    public Long getId() {
        return id;
    }

    public void setId(final Long value) {
        id = value;
    }

    public String getName() {
        return name;
    }

    public void setName(final String value) {
        name = value;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(final UUID value) {
        uuid = value;
    }

    public List<NotificationRecipient> getRecipients() {
        return recipients;
    }

    public void setRecipients(final List<NotificationRecipient> value) {
        recipients = value;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(final Organization value) {
        organization = value;
    }

    /**
     * Converts the ebean model to an internal model.
     *
     * @return an internal model representing this ebean model
     */
    public models.internal.NotificationGroup toInternal() {
        return new DefaultNotificationGroup.Builder()
                .setId(uuid)
                .setName(name)
                .setEntries(recipients.stream().map(NotificationRecipient::toInternal).collect(Collectors.toList()))
                .build();
    }

    /**
     * Finds a {@link NotificationGroup} when given a {@link models.internal.NotificationGroup}.
     *
     * @param group The organization to lookup.
     * @return The notification group from the database.
     */
    @Nullable
    public static NotificationGroup findByNotificationGroup(@Nonnull final models.internal.NotificationGroup group) {
        return FINDER.query()
                .where()
                .eq("uuid", group.getId())
                .findUnique();
    }

    private static final Finder<Long, NotificationGroup> FINDER = new Finder<>(NotificationGroup.class);
}
// CHECKSTYLE.ON: MemberNameCheck
