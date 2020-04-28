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
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import net.sf.oval.constraint.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Model class that represents a metric names response.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
@Loggable
public final class MetricNamesResponse {
    public List<String> getResults() {
        return _results;
    }

    @JsonAnyGetter
    public Map<String, Object> getOtherArgs() {
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
        final MetricNamesResponse otherMetricNamesResponse = (MetricNamesResponse) o;
        return Objects.equals(_results, otherMetricNamesResponse._results)
                && Objects.equals(_otherArgs, otherMetricNamesResponse._otherArgs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_results, _otherArgs);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("results", _results)
                .add("otherArgs", _otherArgs)
                .toString();
    }

    private MetricNamesResponse(final Builder builder) {
        _results = builder._results;
        _otherArgs = ImmutableMap.copyOf(builder._otherArgs);
    }

    private final ImmutableList<String> _results;
    private final ImmutableMap<String, Object> _otherArgs;

    /**
     * Implementation of the builder pattern for a {@link MetricNamesResponse}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends ThreadLocalBuilder<MetricNamesResponse> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(MetricNamesResponse::new);
        }

        /**
         * Sets the results. Required. Cannot be null.
         *
         * @param value the results
         * @return this {@link Builder}
         */
        public Builder setResults(final ImmutableList<String> value) {
            _results = value;
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
            _results = ImmutableList.of();
            _otherArgs = Maps.newHashMap();
        }

        @NotNull
        private ImmutableList<String> _results = ImmutableList.of();
        @NotNull
        private Map<String, Object> _otherArgs = Maps.newHashMap();
    }
}
