/*
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
package models.view;

import com.arpnetworking.commons.builder.OvalBuilder;
import net.sf.oval.constraint.NotNull;

import java.net.URI;

/**
 * Represents an webhook notification entry.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class WebHookNotificationEntry extends NotificationEntry {
    /**
     * Constructs a view model from an internal model.
     *
     * @param entry the internal model
     * @return a new view model
     */
    public static WebHookNotificationEntry fromInternal(final models.internal.impl.WebHookNotificationEntry entry) {
        return new WebHookNotificationEntry.Builder()
                .setAddress(entry.getAddress())
                .build();
    }

    public URI getAddress() {
        return _address;
    }

    @Override
    public models.internal.NotificationEntry toInternal() {
        return new models.internal.impl.WebHookNotificationEntry.Builder()
                .setAddress(_address)
                .build();
    }

    private WebHookNotificationEntry(final Builder builder) {
        _address = builder._address;
    }

    private final URI _address;

    /**
     * Implementation of the builder pattern for an {@link WebHookNotificationEntry}.
     *
     * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
     */
    public static final class Builder extends OvalBuilder<WebHookNotificationEntry> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(WebHookNotificationEntry::new);
        }

        /**
         * Sets the address.
         * @param value address
         * @return this {@link Builder}
         */
        public Builder setAddress(final URI value) {
            this._address = value;
            return this;
        }
        @NotNull
        private URI _address;
    }
}
