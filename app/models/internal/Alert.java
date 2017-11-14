/**
 * Copyright 2015 Groupon.com
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

import org.joda.time.Period;

import java.util.UUID;

/**
 * Internal model interface for an alert.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public interface Alert {

    /**
     * The unique identifier of the alert.
     *
     * @return The unique identifier of the alert.
     */
    UUID getId();

    /**
     * The organization that owns the alert.
     *
     * @return The organization that owns the alert.
     */
    Organization getOrganization();

    /**
     * The name of the alert.
     *
     * @return The name of the alert.
     */
    String getName();

    /**
     * The query to execute.
     *
     * @return The query to execute.
     */
    String getQuery();

    /**
     * The period to evaluate the condition in.
     *
     * @return The period to evaluate the condition in.
     */
    Period getPeriod();

    /**
     * Nagios specific extensions.
     *
     * @return Nagios specific extensions.
     */
    NagiosExtension getNagiosExtension();

    /**
     * UUID of the notification group to notify when the alert triggers.
     *
     * @return The {@link NotificationGroup}'s UUID.
     */
    NotificationGroup getNotificationGroup();
}
