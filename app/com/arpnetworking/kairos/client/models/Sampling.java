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

import com.arpnetworking.commons.builder.OvalBuilder;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
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
public final class Sampling {
    public SamplingUnit getUnit() {
        return _unit;
    }

    public long getValue() {
        return _value;
    }

    @JsonAnyGetter
    public ImmutableMap<String, Object> getExtraFields() {
        return _extraFields;
    }

    private Sampling(final Builder builder) {
        _unit = builder._unit;
        _value = builder._value;
        _extraFields = ImmutableMap.copyOf(builder._extraFields);
    }

    private final SamplingUnit _unit;
    private final int _value;
    private final ImmutableMap<String, Object> _extraFields;

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
                && Objects.equals(_extraFields, sampling._extraFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_unit, _value, _extraFields);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("_unit", _unit)
                .add("_value", _value)
                .add("_extraFields", _extraFields)
                .toString();
    }

    /**
     * Implementation of the builder pattern for a {@link Sampling}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends OvalBuilder<Sampling> {
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
         * Sets an extra generic field on this query. Optional. Cannot be null.
         *
         * @param key the extra field name
         * @param value the extra field value
         * @return this {@link Metric.Builder}
         */
        @JsonAnySetter
        public Builder setExtraField(final String key, final Object value) {
            _extraFields.put(key, value);
            return this;
        }

        @JsonProperty("value")
        @Min(1)
        @NotNull
        private Integer _value = 1;

        @JsonProperty("unit")
        @NotNull
        private SamplingUnit _unit = SamplingUnit.MINUTES;

        @NotNull
        private Map<String, Object> _extraFields = Maps.newHashMap();
    }
}
