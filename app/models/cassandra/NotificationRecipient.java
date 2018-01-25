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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import models.internal.NotificationEntry;
import models.internal.impl.DefaultEmailNotificationEntry;
import models.internal.impl.WebHookNotificationEntry;

/**
 * Represents a notification recipient.  Polymorphic deserialization is handled by Jackson through a TypeCodec.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "email", value = EmailNotificationRecipient.class),
        @JsonSubTypes.Type(name = "webhook", value = WebHookNotificationRecipient.class)})
public interface NotificationRecipient {
    /**
     * Converts this model to an internal model.
     *
     * @return a new internal model
     */
    NotificationEntry toInternal();

    /**
     * Creates a notification recipient from an internal model.
     *
     * @param recipient the internal model
     * @return a new notification recipient
     */
    static NotificationRecipient fromInternal(final NotificationEntry recipient) {
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
}
