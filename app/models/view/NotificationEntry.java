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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * Represents a notification entry.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = EmailNotificationEntry.class, name = "email"),
})
public abstract class NotificationEntry {
    @JsonAnyGetter
    public Map<String, Object> getOther() {
        return _other;
    }

    /**
     * Adds an "unknown" arg.
     *
     * @param key key for the entry
     * @param value value for the entry
     */
    @JsonAnySetter
    public void addOtherArg(final String key, final Object value) {
        _other.put(key, value);
    }

    /**
     * Convert this view model to an internal model.
     *
     * @return an internal model
     */
    public abstract models.internal.NotificationEntry toInternal();

    private Map<String, Object> _other = Maps.newHashMap();
}
