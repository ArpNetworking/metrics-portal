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
import com.fasterxml.jackson.annotation.JsonProperty;
import net.sf.oval.constraint.Min;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

/**
 * Model class to represent the sampling field of an aggregator.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class Sampling {
    public SamplingUnit getUnit() {
        return _unit;
    }

    public int getValue() {
        return _value;
    }

    private Sampling(final Builder builder) {
        _unit = builder._unit;
        _value = builder._value;
    }

    private final SamplingUnit _unit;
    private final int _value;

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

        @JsonProperty("value")
        @Min(1)
        @NotNull
        private Integer _value = 1;

        @JsonProperty("unit")
        @NotNull
        private SamplingUnit _unit = SamplingUnit.MINUTES;
    }
}
