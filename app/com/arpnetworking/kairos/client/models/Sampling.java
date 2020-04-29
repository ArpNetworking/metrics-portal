/*
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
package com.arpnetworking.kairos.client.models;

import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.arpnetworking.logback.annotations.Loggable;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import net.sf.oval.constraint.Min;
import net.sf.oval.constraint.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * Model class to represent the sampling field of an aggregator.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
@Loggable
public final class Sampling {

    public SamplingUnit getUnit() {
        return _unit;
    }

    public int getValue() {
        return _value;
    }

    @JsonAnyGetter
    public ImmutableMap<String, Object> getOtherArgs() {
        return _otherArgs;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Sampling sampling = (Sampling) o;
        return _value == sampling._value
                && _unit == sampling._unit
                && Objects.equals(_otherArgs, sampling._otherArgs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_unit, _value, _otherArgs);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("unit", _unit)
                .add("value", _value)
                .add("otherArgs", _otherArgs)
                .toString();
    }

    private Sampling(final Builder builder) {
        _unit = builder._unit;
        _value = builder._value;
        _otherArgs = ImmutableMap.copyOf(builder._otherArgs);
    }

    private final SamplingUnit _unit;
    private final int _value;
    private final ImmutableMap<String, Object> _otherArgs;

    /**
     * Implementation of the builder pattern for a {@link Sampling}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends ThreadLocalBuilder<Sampling> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(Sampling::new);
        }

        /**
         * Sets the count of units that the Sampling covers.
         *
         * @param value the count of units
         * @return this {@link Builder}
         */
        public Builder setValue(final Integer value) {
            _value = value;
            return this;
        }

        /**
         * Sets the unit the Sampling covers.
         *
         * @param value the unit
         * @return this {@link Builder}
         */
        public Builder setUnit(final SamplingUnit value) {
            _unit = value;
            return this;
        }

        /**
         * Adds an attribute not explicitly modeled by this class. Optional.
         *
         * @param key the attribute name
         * @param value the attribute value
         * @return this {@link Metric.Builder}
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
        public void reset() {
            _value = null;
            _unit = null;
            _otherArgs = Maps.newHashMap();
        }

        @JsonProperty("value")
        @Min(1)
        @NotNull
        private Integer _value;
        @JsonProperty("unit")
        @NotNull
        private SamplingUnit _unit;
        @NotNull
        private Map<String, Object> _otherArgs = Maps.newHashMap();
    }
}
