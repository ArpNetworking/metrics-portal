/*
 * Copyright 2019 Dropbox
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
package com.arpnetworking.kairos.client.models;

import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * Holds the data for a Metric element of the tags query.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class MetricTags {
    public String getName() {
        return _name;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Multimap<String, String> getTags() {
        return _tags;
    }

    @JsonAnyGetter
    public ImmutableMap<String, Object> getOtherArgs() {
        return _otherArgs;
    }

    private MetricTags(final Builder builder) {
        _name = builder._name;
        _tags = builder._tags;
        _otherArgs = ImmutableMap.copyOf(builder._otherArgs);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", _name)
                .add("tags", _tags)
                .add("otherArgs", _otherArgs)
                .toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MetricTags metric = (MetricTags) o;
        return Objects.equals(_name, metric._name)
                && Objects.equals(_tags, metric._tags)
                && Objects.equals(_otherArgs, metric._otherArgs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_name, _tags, _otherArgs);
    }

    private final String _name;
    private final ImmutableMultimap<String, String> _tags;
    private final ImmutableMap<String, Object> _otherArgs;

    /**
     * Implementation of the builder pattern for {@link MetricTags}.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
     */
    public static final class Builder extends ThreadLocalBuilder<MetricTags> {

        /**
         * Public constructor.
         */
        public Builder() {
            super(MetricTags::new);
        }

        /**
         * Sets the name of the metric. Required. Cannot be null or empty.
         *
         * @param value the name of the metric
         * @return this {@link Builder}
         */
        public Builder setName(final String value) {
            _name = value;
            return this;
        }

        /**
         * Sets the tags. Optional. Cannot be null.
         *
         * @param value the tags
         * @return this {@link Builder}
         */
        public Builder setTags(final ImmutableMultimap<String, String> value) {
            _tags = value;
            return this;
        }

        /**
         * Adds an attribute not explicitly modeled by this class. Optional.
         *
         * @param key the attribute name
         * @param value the attribute value
         * @return this {@link Builder}
         */
        @JsonAnySetter
        public Builder addOtherArg(final String key, final Object value) {
            _otherArgs.put(key, value);
            return this;
        }

        /**
         * Sets the attributes not explicitly modeled by this class. Optional.
         *
         * @param value the other attributes
         * @return this {@link Builder}
         */
        @JsonIgnore
        public Builder setOtherArgs(final ImmutableMap<String, Object> value) {
            _otherArgs = value;
            return this;
        }

        @Override
        protected void reset() {
            _name = null;
            _tags = ImmutableMultimap.of();
            _otherArgs = Maps.newHashMap();
        }

        @NotNull
        @NotEmpty
        private String _name;
        @NotNull
        private ImmutableMultimap<String, String> _tags = ImmutableMultimap.of();
        @NotNull
        private Map<String, Object> _otherArgs = Maps.newHashMap();
    }
}
