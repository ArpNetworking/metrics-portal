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
package models.view;

import org.joda.time.DateTime;

import java.util.Map;
import java.util.UUID;

/**
 * Represents a notification.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class Notification {
    public String getDedupeKey() {
        return _dedupeKey;
    }

    public DateTime getTimestamp() {
        return _timestamp;
    }

    public UUID getAlertId() {
        return _alertId;
    }

    public Map<String, Object> getDetails() {
        return _details;
    }

    public void setDedupeKey(final String dedupeKey) {
        _dedupeKey = dedupeKey;
    }

    public void setTimestamp(final DateTime timestamp) {
        _timestamp = timestamp;
    }

    public void setAlertId(final UUID alertId) {
        _alertId = alertId;
    }

    public void setDetails(final Map<String, Object> details) {
        _details = details;
    }

    private String _dedupeKey;
    private DateTime _timestamp;
    private UUID _alertId;
    private Map<String, Object> _details;
}
