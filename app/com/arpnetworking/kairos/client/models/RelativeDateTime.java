/*
 * Copyright 2019 Dropbox Inc.
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
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import net.sf.oval.constraint.MemberOf;
import net.sf.oval.constraint.NotNull;

import java.util.Objects;

/**
 * Defines a relative date-time.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class RelativeDateTime {

    @JsonAnyGetter
    public ImmutableMap<String, Object> getOtherArgs() {
        return _otherArgs;
    }

    public String getUnit() {
        return _unit;
    }

    public Number getValue() {
        return _value;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("unit", _unit)
                .add("value", _value)
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
        final RelativeDateTime that = (RelativeDateTime) o;
        return Objects.equals(_unit, that._unit)
                && Objects.equals(_value, that._value)
                && Objects.equals(_otherArgs, that._otherArgs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_unit, _value, _otherArgs);
    }

    private RelativeDateTime(final Builder builder) {
        _otherArgs = builder._otherArgs;
        _unit = builder._unit;
        _value = builder._value;
    }

    private final ImmutableMap<String, Object> _otherArgs;
    private final String _unit;
    private final Number _value;

    /**
     * Implementation of the builder pattern for {@link RelativeDateTime}.
     */
    public static final class Builder extends ThreadLocalBuilder<RelativeDateTime> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(RelativeDateTime::new);
        }

        /**
         * Adds an attribute not explicitly modeled by this class. Optional.
         *
         * @param key the attribute name
         * @param value the attribute value
         * @return this {@link MetricsQueryResponse.Builder}
         */
        @JsonAnySetter
        public Builder addOtherArg(final String key, final Object value) {
            _otherArgs = new ImmutableMap.Builder<String, Object>().putAll(_otherArgs).put(key, value).build();
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

        /**
         * Sets the unit for the relative date-time value. Required. Cannot be
         * null. Must be one of:
         *
         * <ul>
         *     <li>milliseconds</li>
         *     <li>seconds</li>
         *     <li>minutes</li>
         *     <li>hours</li>
         *     <li>days</li>
         *     <li>weeks</li>
         *     <li>months</li>
         *     <li>years</li>
         * </ul>
         *
         * @param value the unit of the value
         * @return this {@link Builder}
         */
        public Builder setUnit(final String value) {
            _unit = value;
            return this;
        }

        /**
         * Sets the value for the relative date-time value. Required. Cannot be
         * null.
         *
         * @param value the value
         * @return this {@link Builder}
         */
        public Builder setValue(final Number value) {
            _value = value;
            return this;
        }

        @Override
        protected void reset() {
            _otherArgs = ImmutableMap.of();
            _unit = null;
            _value = null;
        }

        @NotNull
        private ImmutableMap<String, Object> _otherArgs = ImmutableMap.of();
        @NotNull
        @MemberOf(value = {"milliseconds", "seconds", "minutes", "hours", "days", "weeks", "months", "years"})
        private String _unit;
        @NotNull
        private Number _value;
    }
}
