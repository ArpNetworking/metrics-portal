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

import models.internal.NotificationEntry;
import models.internal.impl.DefaultEmailNotificationEntry;

import java.util.Objects;

/**
 * Model class for an email notification recipient in cassandra.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class EmailNotificationRecipient implements NotificationRecipient {
    public String getAddress() {
        return _address;
    }

    public void setAddress(final String address) {
        _address = address;
    }

    @Override
    public NotificationEntry toInternal() {
        return new DefaultEmailNotificationEntry.Builder()
                .setAddress(_address)
                .build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EmailNotificationRecipient that = (EmailNotificationRecipient) o;
        return Objects.equals(_address, that._address);
    }

    @Override
    public int hashCode() {

        return Objects.hash(_address);
    }

    private String _address;
}
