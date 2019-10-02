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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Model class to represent the aggregator in a metrics query.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class Aggregator {

    private Aggregator(final Builder builder) {
        _name = builder._name;
        _sampling = Optional.ofNullable(builder._sampling);
        if (!_sampling.isPresent()) {
            _alignSampling = Optional.empty();
        } else {
            _alignSampling = Optional.ofNullable(builder._alignSampling);
        }
        _otherArgs = builder._otherArgs;
        _alignEndTime = Optional.ofNullable(builder._alignEndTime);
        _alignStartTime = Optional.ofNullable(builder._alignStartTime);
    }

    public String getName() {
        return _name;
    }

    @JsonProperty("align_sampling")
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<Boolean> getAlignSampling() {
        return _alignSampling;
    }

    @JsonProperty("align_start_time")
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<Boolean> getAlignStartTime() {
        return _alignStartTime;
    }

    @JsonProperty("align_end_time")
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<Boolean> getAlignEndTime() {
        return _alignEndTime;
    }

    @JsonInclude(value = JsonInclude.Include.NON_ABSENT)
    public Optional<Sampling> getSampling() {
        return _sampling;
    }

    @JsonAnyGetter
    public ImmutableMap<String, Object> getOtherArgs() {
        return _otherArgs;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", _name)
                .add("alignSampling", _alignSampling)
                .add("alignStartTime", _alignStartTime)
                .add("alignEndTime", _alignEndTime)
                .add("sampling", _sampling)
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
        final Aggregator that = (Aggregator) o;
        return Objects.equals(_name, that._name)
                && Objects.equals(_alignSampling, that._alignSampling)
                && Objects.equals(_alignStartTime, that._alignStartTime)
                && Objects.equals(_alignEndTime, that._alignEndTime)
                && Objects.equals(_sampling, that._sampling)
                && Objects.equals(_otherArgs, that._otherArgs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_name, _alignSampling, _alignStartTime, _alignEndTime, _sampling, _otherArgs);
    }

    private final String _name;
    private final Optional<Boolean> _alignSampling;
    private final Optional<Boolean> _alignStartTime;
    private final Optional<Boolean> _alignEndTime;
    private final Optional<Sampling> _sampling;
    private final ImmutableMap<String, Object> _otherArgs;

    /**
     * Implementation of the builder pattern for a an {@link Aggregator}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends OvalBuilder<Aggregator> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(Aggregator::new);
        }

        /**
         * Sets the name of the aggregator. Required. Cannot be null or empty.
         *
         * @param value the name of the aggregator
         * @return this {@link Builder}
         */
        public Builder setName(final String value) {
            _name = value;
            return this;
        }

        /**
         * Sets the sampling of the aggregator. Optional. Defaults to 1 minute.
         *
         * @param value the sampling for the aggregator
         * @return this {@link Builder}
         */
        public Builder setSampling(@Nullable final Sampling value) {
            _sampling = value;
            return this;
        }

        /**
         * Sets the align_sampling of the aggregator. Optional. Defaults to true.
         *
         * @param value the align_sampling for the aggregator
         * @return this {@link Builder}
         */
        @JsonProperty("align_sampling")
        public Builder setAlignSampling(@Nullable final Boolean value) {
            _alignSampling = value;
            return this;
        }

        /**
         * Sets the align_start_time of the aggregator. Optional. Defaults to false.
         *
         * @param value the align_start_time for the aggregator
         * @return this {@link Builder}
         */
        @JsonProperty("align_start_time")
        public Builder setAlignStartTime(@Nullable final Boolean value) {
            _alignStartTime = value;
            return this;
        }

        /**
         * Sets the align_end_time of the aggregator. Optional. Defaults to false.
         *
         * @param value the align_end_time for the aggregator
         * @return this {@link Builder}
         */
        @JsonProperty("align_end_time")
        public Builder setAlignEndTime(@Nullable final Boolean value) {
            _alignEndTime = value;
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

        @NotNull
        @NotEmpty
        private String _name;
        private Boolean _alignSampling;
        private Sampling _sampling;
        private Boolean _alignEndTime;
        private Boolean _alignStartTime;
        @NotNull
        private ImmutableMap<String, Object> _otherArgs = ImmutableMap.of();
    }
}
