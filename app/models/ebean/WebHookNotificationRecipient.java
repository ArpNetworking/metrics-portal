/**
 * Copyright 2018 Smartsheet.com
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

import com.google.common.base.Objects;
import models.internal.NotificationEntry;
import models.internal.impl.WebHookNotificationEntry;

import java.net.URI;
import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

/**
 * An email address to send mail to.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@DiscriminatorValue("webhook")
public class WebHookNotificationRecipient extends NotificationRecipient {
    @Column(name = "value")
    private URI address;
    public URI getAddress() {
        return address;
    }

    public void setAddress(final URI value) {
        address = value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final WebHookNotificationRecipient that = (WebHookNotificationRecipient) o;
        return Objects.equal(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(address);
    }

    @Override
    public NotificationEntry toInternal() {
        return new WebHookNotificationEntry.Builder()
                .setAddress(address)
                .build();
    }
}
// CHECKSTYLE.ON: MemberNameCheck
