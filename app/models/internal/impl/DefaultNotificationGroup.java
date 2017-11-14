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
package models.internal.impl;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.google.common.collect.Lists;
import models.internal.NotificationEntry;
import models.internal.NotificationGroup;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A simple {@link NotificationGroup} implementation.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class DefaultNotificationGroup implements NotificationGroup {
    @Override
    public UUID getId() {
        return _id;
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public List<NotificationEntry> getEntries() {
        return _entries;
    }

    @Override
    public models.view.NotificationGroup toView() {
        final models.view.NotificationGroup viewGroup = new models.view.NotificationGroup();
        viewGroup.setId(_id);
        viewGroup.setName(_name);
        viewGroup.setEntries(internalEntriesToViewEntry(_entries));
        return viewGroup;
    }

    private List<models.view.NotificationEntry> internalEntriesToViewEntry(final List<NotificationEntry> entries) {
        return entries.stream().map(NotificationEntry::toView).collect(Collectors.toList());
    }

    private DefaultNotificationGroup(final Builder builder) {
        _id = builder._id;
        _name = builder._name;
        _entries = Lists.newArrayList(builder._entries);
    }

    private final UUID _id;
    private final String _name;
    private final List<NotificationEntry> _entries;

    /**
     * Implementation of the builder parttern for a {@link DefaultNotificationGroup}.
     */
    public static final class Builder extends OvalBuilder<DefaultNotificationGroup> {

        /**
         * Public constructor.
         */
        public Builder() {
            super(DefaultNotificationGroup::new);
        }

        /**
         * The identifier. Required. Cannot be null.
         *
         * @param value The identifier.
         * @return This instance of {@link Builder}.
         */
        public Builder setId(final UUID value) {
            _id = value;
            return this;
        }

        /**
         * The name. Required. Cannot be null or empty.
         *
         * @param value The name.
         * @return This instance of {@link Builder}.
         */
        public Builder setName(final String value) {
            _name = value;
            return this;
        }

        /**
         * The name. Required. Cannot be null or empty.
         *
         * @param value The name.
         * @return This instance of {@link Builder}.
         */
        public Builder setEntries(final List<NotificationEntry> value) {
            _entries = value;
            return this;
        }

        @NotNull
        private UUID _id;
        @NotNull
        @NotEmpty
        private String _name;
        @NotNull
        private List<NotificationEntry> _entries = Lists.newArrayList();
    }
}
