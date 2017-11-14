/*
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
package models.view;

import com.arpnetworking.commons.builder.OvalBuilder;
import models.internal.impl.DefaultEmailNotificationEntry;

/**
 * Represents an email notification entry.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class EmailNotificationEntry extends NotificationEntry {
    /**
     * Constructs a view model from an internal model.
     *
     * @param entry the internal model
     * @return a new view model
     */
    public static EmailNotificationEntry fromInternal(final DefaultEmailNotificationEntry entry) {
        return new EmailNotificationEntry.Builder()
                .setAddress(entry.getAddress())
                .build();
    }

    public String getAddress() {
        return _address;
    }

    @Override
    public models.internal.NotificationEntry toInternal() {
        return new DefaultEmailNotificationEntry.Builder()
                .setAddress(_address)
                .build();
    }

    private EmailNotificationEntry(final Builder builder) {
        _address = builder._address;
    }

    private final String _address;

    /**
     * Implementation of the builder pattern for an {@link EmailNotificationEntry}.
     *
     * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
     */
    public static final class Builder extends OvalBuilder<EmailNotificationEntry> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(EmailNotificationEntry::new);
        }

        /**
         * Sets the address.
         * @param value address
         * @return this {@link Builder}
         */
        public Builder setAddress(final String value) {
            this._address = value;
            return this;
        }
        private String _address;
    }
}
