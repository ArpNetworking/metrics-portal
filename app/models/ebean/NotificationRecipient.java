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

import models.internal.NotificationEntry;
import models.internal.impl.DefaultEmailNotificationEntry;
import models.internal.impl.WebHookNotificationEntry;

import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * Represents a recipient of a notification.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@Table(name = "notification_recipients", schema = "portal")
@DiscriminatorColumn(name = "type")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class NotificationRecipient {
    /**
     * Creates an ebean model from an internal model.
     *
     * @param recipient an internal model
     * @return an ebean model
     */
    public static NotificationRecipient fromInternal(final NotificationEntry recipient) {
        if (recipient instanceof DefaultEmailNotificationEntry) {
            final DefaultEmailNotificationEntry emailRecipient = (DefaultEmailNotificationEntry) recipient;
            final EmailNotificationRecipient notificationRecipient = new EmailNotificationRecipient();
            notificationRecipient.setAddress(emailRecipient.getAddress());
            return notificationRecipient;
        } else if (recipient instanceof WebHookNotificationEntry) {
            final WebHookNotificationEntry webHookNotificationEntry = (WebHookNotificationEntry) recipient;
            final WebHookNotificationRecipient notificationRecipient = new WebHookNotificationRecipient();
            notificationRecipient.setAddress(webHookNotificationEntry.getAddress());
            return notificationRecipient;
        }
        throw new IllegalArgumentException("Unknown recipient type \"" + recipient.getClass().getCanonicalName() + "\"");
    }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "notificationgroup")
    private NotificationGroup group;

    public Long getId() {
        return id;
    }

    public void setId(final Long value) {
        id = value;
    }


    public NotificationGroup getGroup() {
        return group;
    }

    public void setGroup(final NotificationGroup value) {
        group = value;
    }

    /**
     * Converts the ebean model to an internal model.
     *
     * @return an internal model representing this ebean model
     */
    public abstract models.internal.NotificationEntry toInternal();
}
// CHECKSTYLE.ON: MemberNameCheck
