/*
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
package models.internal.impl;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import models.internal.MetricsQuery;
import models.internal.Organization;
import models.internal.alerts.Alert;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Default internal model implementation for an alert.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
@Loggable
public final class DefaultAlert implements Alert {

    private final UUID _id;
    private final Organization _organization;
    private final String _name;
    private final String _description;
    private final MetricsQuery _query;
    private final boolean _enabled;
    private final ImmutableMap<String, Object> _additionalMetadata;

    private DefaultAlert(final Builder builder) {
        _id = builder._id;
        _organization = builder._organization;
        _name = builder._name;
        _description = builder._description;
        _query = builder._query;
        _enabled = builder._enabled;
        _additionalMetadata = builder._additionalMetadata;
    }

    @Override
    public UUID getId() {
        return _id;
    }

    @Override
    public Organization getOrganization() {
        return _organization;
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public String getDescription() {
        return _description;
    }

    @Override
    public MetricsQuery getQuery() {
        return _query;
    }

    @Override
    public boolean isEnabled() {
        return _enabled;
    }

    @Override
    public ImmutableMap<String, Object> getAdditionalMetadata() {
        return _additionalMetadata;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultAlert that = (DefaultAlert) o;
        return _enabled == that._enabled
                && Objects.equal(_id, that._id)
                && Objects.equal(_organization, that._organization)
                && Objects.equal(_name, that._name)
                && Objects.equal(_description, that._description)
                && Objects.equal(_query, that._query)
                && Objects.equal(_additionalMetadata, that._additionalMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(_id, _organization, _name, _description, _query, _enabled, _additionalMetadata);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", _id)
                .add("organization", _organization)
                .add("name", _name)
                .add("description", _description)
                .add("query", _query)
                .add("enabled", _enabled)
                .add("additionalMetadata", _additionalMetadata)
                .toString();
    }

    /**
     * Builder implementation for {@link DefaultAlert}.
     */
    public static final class Builder extends OvalBuilder<DefaultAlert> {

        @NotNull
        private UUID _id;
        @NotNull
        private Organization _organization;
        @NotNull
        @NotEmpty
        private String _name;
        @NotNull
        private String _description;
        @NotNull
        private MetricsQuery _query;
        @NotNull
        private Boolean _enabled;
        private ImmutableMap<String, Object> _additionalMetadata;

        /**
         * Public constructor.
         */
        public Builder() {
            super(DefaultAlert::new);
            _additionalMetadata = ImmutableMap.of();
        }

        /**
         * The alert identifier. Required. Cannot be null.
         *
         * @param value The identifier.
         * @return This instance of {@link Builder}.
         */
        public Builder setId(final UUID value) {
            _id = value;
            return this;
        }

        /**
         * The alert organization. Required. Cannot be null.
         *
         * @param value The organization.
         * @return This instance of {@link Builder}.
         */
        public Builder setOrganization(final Organization value) {
            _organization = value;
            return this;
        }

        /**
         * The alert name. Required. Cannot be null or empty.
         *
         * @param value The name.
         * @return This instance of {@link Builder}.
         */
        public Builder setName(final String value) {
            _name = value;
            return this;
        }

        /**
         * The alert description. Defaults to an empty description.
         *
         * @param value The description.
         * @return This instance of {@link Builder}.
         */
        public Builder setDescription(final String value) {
            _description = value;
            return this;
        }

        /**
         * The alert query. Required. Cannot be null.
         *
         * @param value The query.
         * @return This instance of {@link Builder}.
         */
        public Builder setQuery(final MetricsQuery value) {
            _query = value;
            return this;
        }

        /**
         * The enabled flag. Required.
         *
         * @param value Whether or not this alert is enabled.
         * @return This instance of {@link Builder}.
         */
        public Builder setEnabled(final boolean value) {
            _enabled = value;
            return this;
        }

        /**
         * The additional metadata. Defaults to an empty map.
         *
         * @param value The period.
         * @return This instance of {@link Builder}.
         */
        public Builder setAdditionalMetadata(final Map<String, Object> value) {
            _additionalMetadata = ImmutableMap.copyOf(value);
            return this;
        }
    }
}
