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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    @JsonAnyGetter
    public ImmutableMap<String, Object> getExtraFields() {
        return _extraFields;
    }

    private MetricsQuery(final Builder builder) {
        _startTime = builder._startTime;
        _endTime = Optional.ofNullable(builder._endTime);
        _metrics = builder._metrics;
        _extraFields = ImmutableMap.copyOf(builder._extraFields);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("_startTime", _startTime)
                .add("_endTime", _endTime)
                .add("_metrics", _metrics)
                .add("_extraFields", _extraFields)
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
        final MetricsQuery that = (MetricsQuery) o;
        return Objects.equals(_startTime, that._startTime)
                && Objects.equals(_endTime, that._endTime)
                && Objects.equals(_metrics, that._metrics)
                && Objects.equals(_extraFields, that._extraFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_startTime, _endTime, _metrics, _extraFields);
    }

    private final Instant _startTime;
    private final Optional<Instant> _endTime;
    private final ImmutableList<Metric> _metrics;
    private final ImmutableMap<String, Object> _extraFields;

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

        /**
         * Sets the absolute start time in milliseconds.
         *
         * @param millis the start time in milliseconds
         * @return this {@link Builder}
         */
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

        /**
         * Sets the absolute end time in milliseconds.
         *
         * @param millis the end time in milliseconds
         * @return this {@link Builder}
         */
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

        /**
         * Sets an extra generic field on this query. Optional. Cannot be null.
         *
         * @param key the extra field name
         * @param value the extra field value
         * @return this {@link Builder}
         */
        @JsonAnySetter
        public Builder setExtraField(final String key, final Object value) {
            _extraFields.put(key, value);
            return this;
        }


        @NotNull
        private Instant _startTime;
        private Instant _endTime;
        @NotNull
        @NotEmpty
        private ImmutableList<Metric> _metrics = ImmutableList.of();
        @NotNull
        private Map<String, Object> _extraFields = Maps.newHashMap();
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

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("_otherArgs", _otherArgs)
                    .add("_name", _name)
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
            final GroupBy groupBy = (GroupBy) o;
            return Objects.equals(_otherArgs, groupBy._otherArgs)
                    && Objects.equals(_name, groupBy._name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_otherArgs, _name);
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
