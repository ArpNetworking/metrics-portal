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
package models.internal;

import org.joda.time.DateTime;

import java.util.Map;

/**
 * A notification event that needs to be sent to recipients.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public interface NotificationEvent {
    /**
     * A key to deduplicate notifications.
     *
     * @return the deduplication key
     */
    String getDeduplicationKey();

    /**
     * The timestamp of the notification.
     *
     * @return the timestamp
     */
    DateTime getTimestamp();

    /**
     * A map of additional details about the notification.
     *
     * @return {@link Map} of details
     */
    Map<String, Object> getDetails();
}
