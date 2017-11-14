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

import com.arpnetworking.commons.builder.OvalBuilder;
import com.google.common.collect.ImmutableList;
import models.internal.impl.DefaultNotificationGroup;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Represents a group of notification entries.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class NotificationGroup {
    public UUID getId() {
        return _id;
    }

    public String getName() {
        return _name;
    }

    public List<NotificationEntry> getEntries() {
        return _entries;
    }

    /**
     * Convert this view model into an internal model.
     *
     * @return an internal model
     */
    public models.internal.NotificationGroup toInternal() {
        return new DefaultNotificationGroup.Builder()
                .setName(_name)
                .setId(_id)
                .setEntries(_entries.stream().map(NotificationEntry::toInternal).collect(Collectors.toList()))
                .build();
    }

    /**
     * Constructs a view model from an internal model.
     *
     * @param group the internal model
     * @return a new view model
     */
    public static NotificationGroup fromInternal(final models.internal.NotificationGroup group) {
        return new Builder()
                .setId(group.getId())
                .setName(group.getName())
                .setEntries(group.getEntries().stream().map(NotificationEntry::fromInternal).collect(ImmutableList.toImmutableList()))
                .build();
    }

    private NotificationGroup(final Builder builder) {
        _id = builder._id;
        _name = builder._name;
        _entries = builder._entries;
    }

    private final UUID _id;
    private final String _name;
    private final ImmutableList<NotificationEntry> _entries;

    /**
     * Implementation of the builder pattern for an {@link NotificationGroup}.
     *
     * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
     */
    public static final class Builder extends OvalBuilder<NotificationGroup> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(NotificationGroup::new);
        }

        /**
         * Sets the id.
         * @param value the value
         * @return this {@link Builder}
         */
        public Builder setId(final UUID value) {
            this._id = value;
            return this;
        }

        /**
         * Sets the name.
         * @param value the value
         * @return this {@link Builder}
         */
        public Builder setName(final String value) {
            this._name = value;
            return this;
        }

        /**
         * Sets the group entries.
         * @param value the value
         * @return this {@link Builder}
         */
        public Builder setEntries(final ImmutableList<NotificationEntry> value) {
            this._entries = value;
            return this;
        }

        @NotNull
        private UUID _id;
        @NotNull
        @NotEmpty
        private String _name;
        private ImmutableList<NotificationEntry> _entries = ImmutableList.of();
    }
}
