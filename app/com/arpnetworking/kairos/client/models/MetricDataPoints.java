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
import com.arpnetworking.logback.annotations.Loggable;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import net.sf.oval.constraint.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Model class to represent the data points for a metric.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
@Loggable
public final class MetricDataPoints {

    public String getName() {
        return _name;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> getTtl() {
        return _ttl;
    }

    public ImmutableMap<String, String> getTags() {
        return _tags;
    }

    public ImmutableList<DataPoint> getDatapoints() {
        return _datapoints;
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
        final MetricDataPoints otherMetricDataPoints = (MetricDataPoints) o;
        return Objects.equals(_name, otherMetricDataPoints._name)
                && Objects.equals(_ttl, otherMetricDataPoints._ttl)
                && Objects.equals(_tags, otherMetricDataPoints._tags)
                && Objects.equals(_datapoints, otherMetricDataPoints._datapoints)
                && Objects.equals(_otherArgs, otherMetricDataPoints._otherArgs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_name, _ttl, _tags, _datapoints, _otherArgs);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", _name)
                .add("ttl", _ttl)
                .add("tags", _tags)
                .add("datapoints", _datapoints)
                .add("otherArgs", _otherArgs)
                .toString();
    }

    private MetricDataPoints(final Builder builder) {
        _name = builder._name;
        _ttl = Optional.ofNullable(builder._ttl);
        _tags = builder._tags;
        _datapoints = builder._datapoints;
        _otherArgs = ImmutableMap.copyOf(builder._otherArgs);
    }

    private final String _name;
    private final Optional<Integer> _ttl;
    private final ImmutableMap<String, String> _tags;
    private final ImmutableList<DataPoint> _datapoints;
    private final ImmutableMap<String, Object> _otherArgs;

    /**
     * Implementation of the builder pattern for a {@link MetricDataPoints}.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
     */
    public static final class Builder extends ThreadLocalBuilder<MetricDataPoints> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(MetricDataPoints::new);
        }

        /**
         * Sets the metric name. Required. Cannot be null.
         *
         * @param value the name of the metric
         * @return this {@link Builder}
         */
        public Builder setName(final String value) {
            _name = value;
            return this;
        }

        /**
         * Sets the time to live. Optional. Default is not set which defaults
         * to the time to live in KairosDb server configuration.
         *
         * @param value the time to live
         * @return this {@link Builder}
         */
        public Builder setTtl(@Nullable final Integer value) {
            _ttl = value;
            return this;
        }

        /**
         * Sets the tags associated with the data.
         *
         * @param value the tags
         * @return this {@link Builder}
         */
        public Builder setTags(final ImmutableMap<String, String> value) {
            _tags = value;
            return this;
        }

        /**
         * Sets the data points.
         *
         * @param value the data points
         * @return this {@link Builder}
         */
        public Builder setDatapoints(final ImmutableList<DataPoint> value) {
            _datapoints = value;
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
            _name = null;
            _ttl = null;
            _tags = ImmutableMap.of();
            _datapoints = ImmutableList.of();
            _otherArgs = Maps.newHashMap();
        }

        @NotNull
        private String _name;
        @Nullable
        private Integer _ttl;
        @NotNull
        private ImmutableMap<String, String> _tags = ImmutableMap.of();
        @NotNull
        private ImmutableList<DataPoint> _datapoints = ImmutableList.of();
        @NotNull
        private Map<String, Object> _otherArgs = Maps.newHashMap();
    }
}
