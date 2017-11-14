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
package models.view;

import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;

import java.util.UUID;

/**
 * View model of <code>Alert</code>. Play view models are mutable.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
@Loggable
public final class Alert {

    public void setId(final String value) {
        _id = value;
    }

    public String getId() {
        return _id;
    }

    public void setName(final String value) {
        _name = value;
    }

    public String getName() {
        return _name;
    }

    public String getQuery() {
        return _query;
    }

    public void setQuery(final String query) {
        _query = query;
    }

    public void setPeriod(final String value) {
        _period = value;
    }

    public String getPeriod() {
        return _period;
    }

    public void setExtensions(final ImmutableMap<String, Object> extensions) {
        _extensions = extensions;
    }

    public ImmutableMap<String, Object> getExtensions() {
        return _extensions;
    }

    public UUID getNotificationGroupId() {
        return _notificationGroupId;
    }

    public void setNotificationGroupId(final UUID notificationGroupId) {
        _notificationGroupId = notificationGroupId;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", Integer.toHexString(System.identityHashCode(this)))
                .add("class", this.getClass())
                .add("Id", _id)
                .add("Name", _name)
                .add("Query", _query)
                .add("Period", _period)
                .add("Extensions", _extensions)
                .toString();
    }

    private String _id;
    private String _name;
    private String _query;
    private String _period;
    private ImmutableMap<String, Object> _extensions;
    private UUID _notificationGroupId;
}
