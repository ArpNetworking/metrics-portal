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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Model class to represent a metrics query.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class MetricsQuery {
    /**
     * Gets the start time of the query in epoch milliseconds (inclusive).
     *
     * @return the start time in milliseconds
     */
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    @JsonProperty("start_absolute")
    private long startMillis() {
        return _startTime.toEpochMilli();
    }

    /**
     * Gets the end time of the query in epoch milliseconds (inclusive).
     *
     * @return the end time in milliseconds
     */
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    @JsonProperty("end_absolute")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private long endMillis() {
        return _endTime.map(Instant::toEpochMilli).orElse(0L);
    }

    @JsonIgnore
    public Instant getStartTime() {
        return _startTime;
    }

    @JsonIgnore
    public Optional<Instant> getEndTime() {
        return _endTime;
    }

    @JsonProperty(value = "metrics")
    public ImmutableList<Metric> getMetrics() {
        return _metrics;
    }

    private MetricsQuery(final Builder builder) {
        _startTime = builder._startTime;
        _endTime = Optional.ofNullable(builder._endTime);
        _metrics = builder._metrics;
    }

    private final Instant _startTime;
    private final Optional<Instant> _endTime;
    private final ImmutableList<Metric> _metrics;

    /**
     * Implementation of the builder pattern for MetricsQuery.
     */
    public static final class Builder extends OvalBuilder<MetricsQuery> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(MetricsQuery::new);
        }

        /**
         * Sets the start time of the query. Required. Cannot be null.
         *
         * @param value the start time
         * @return this {@link Builder}
         */
        public Builder setStartTime(final Instant value) {
            _startTime = value;
            return this;
        }

        @JsonProperty("start_absolute")
        public Builder setStartTimeMillis(final Long millis) {
            _startTime = Instant.ofEpochMilli(millis);
            return this;
        }

        /**
         * Sets the end time of the query. Null is used as "now" from KairosDB.  Optional. Default is null.
         *
         * @param value the end time
         * @return this {@link Builder}
         */
        public Builder setEndTime(@Nullable final Instant value) {
            _endTime = value;
            return this;
        }

        @JsonProperty("end_absolute")
        public Builder setEndTimeMillis(final Long millis) {
            _endTime = Instant.ofEpochMilli(millis);
            return this;
        }

        /**
         * Sets list of metrics. Required. Cannot be null or empty.
         *
         * @param value the metrics
         * @return this {@link Builder}
         */
        public Builder setMetrics(final ImmutableList<Metric> value) {
            _metrics = value;
            return this;
        }

        public List<Metric> getMetrics() {
            return _metrics;
        }

        @NotNull
        private Instant _startTime;
        private Instant _endTime;
        @NotNull
        @NotEmpty
        private ImmutableList<Metric> _metrics = ImmutableList.of();
    }

    /**
     * Model class to represent the group by object in a metrics query.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class GroupBy {
        @JsonAnyGetter
        public Map<String, Object> getOtherArgs() {
            return _otherArgs;
        }

        public String getName() {
            return _name;
        }

        private GroupBy(final Builder builder) {
            _otherArgs = builder._otherArgs;
            _name = builder._name;
        }

        private final Map<String, Object> _otherArgs;
        private final String _name;

        /**
         * Implementation of the builder pattern for a {@link GroupBy}.
         *
         * @author Brandon Arp (brandon dot arp at smartsheet dot com)
         */
        public static final class Builder extends OvalBuilder<GroupBy> {
            /**
             * Public constructor.
             */
            public Builder() {
                super(GroupBy::new);
            }

            /**
             * Sets the name. Required. Cannot be null or empty.
             *
             * @param value the name
             * @return this {@link Builder}
             */
            public Builder setName(final String value) {
                _name = value;
                return this;
            }

            /**
             * Adds an "unknown" parameter. Optional.
             *
             * @param key key for the entry
             * @param value value for the entry
             * @return this {@link Builder}
             */
            @JsonAnySetter
            public Builder addOtherArg(final String key, final Object value) {
                _otherArgs = new ImmutableMap.Builder<String, Object>().putAll(_otherArgs).put(key, value).build();
                return this;
            }

            @NotNull
            @NotEmpty
            private String _name;
            @NotNull
            private ImmutableMap<String, Object> _otherArgs = ImmutableMap.of();
        }
    }
}
