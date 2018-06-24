/**
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
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.sf.oval.constraint.Min;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;
import org.joda.time.DateTime;
import org.joda.time.Period;

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
        return _startTime.getMillis();
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
        return _endTime.map(DateTime::getMillis).orElse(0L);
    }

    @JsonIgnore
    public DateTime getStartTime() {
        return _startTime;
    }

    @JsonIgnore
    @Nullable
    public DateTime getEndTime() {
        return _endTime.orElse(null);
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

    private final DateTime _startTime;
    private final Optional<DateTime> _endTime;
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
        public Builder setStartTime(final DateTime value) {
            _startTime = value;
            return this;
        }

        /**
         * Sets the end time of the query. Null is used as "now" from KairosDB.  Optional. Default is null.
         *
         * @param value the end time
         * @return this {@link Builder}
         */
        public Builder setEndTime(final DateTime value) {
            _endTime = value;
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
        private DateTime _startTime;
        private DateTime _endTime;
        @NotNull
        @NotEmpty
        private ImmutableList<Metric> _metrics = ImmutableList.of();
    }

    /**
     * Holds the data for a Metric element of the query.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Metric {
        public String getName() {
            return _name;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public Multimap<String, String> getTags() {
            return _tags;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public ImmutableList<Aggregator> getAggregators() {
            return _aggregators;
        }

        @JsonProperty("group_by")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public ImmutableList<GroupBy> getGroupBy() {
            return _groupBy;
        }

        private Metric(final Builder builder) {
            _name = builder._name;
            _tags = builder._tags;
            _aggregators = builder._aggregators;
            _groupBy = builder._groupBy;
        }

        private final String _name;
        private final ImmutableMultimap<String, String> _tags;
        private final ImmutableList<Aggregator> _aggregators;
        private final ImmutableList<GroupBy> _groupBy;

        /**
         * Implementation of the builder pattern for {@link Metric}.
         *
         * @author Brandon Arp (brandon dot arp at smartsheet dot com)
         */
        public static final class Builder extends OvalBuilder<Metric> {

            /**
             * Public constructor.
             */
            public Builder() {
                super(Metric::new);
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
             * Sets the group by. Optional. Cannot be null.
             *
             * @param value the group by clauses
             * @return this {@link Builder}
             */
            public Builder setGroupBy(final ImmutableList<GroupBy> value) {
                _groupBy = value;
                return this;
            }

            /**
             * Sets the aggregators. Optional. Cannot be null.
             *
             * @param value the aggregators
             * @return this {@link Builder}
             */
            public Builder setAggregators(final ImmutableList<Aggregator> value) {
                _aggregators = value;
                return this;
            }

            /**
             * Add an aggregator. Optional. Cannot be null.
             *
             * @param value the aggregator to add
             * @return this {@link Builder}
             */
            public Builder addAggregator(final Aggregator value) {
                _aggregators = ImmutableList.<Aggregator>builder().addAll(_aggregators).add(value).build();
                return this;
            }

            @NotNull
            @NotEmpty
            private String _name;

            @NotNull
            private ImmutableList<Aggregator> _aggregators = ImmutableList.of();

            @NotNull
            private ImmutableList<GroupBy> _groupBy = ImmutableList.of();

            @NotNull
            private ImmutableMultimap<String, String> _tags = ImmutableMultimap.of();
        }
    }

    /**
     * Model class to represent the aggregator in a metrics query.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Aggregator {
        private Aggregator(final Builder builder) {
            _name = builder._name;
            _sampling = builder._sampling;
            if (_sampling == null) {
                _alignSampling = null;
            } else {
                _alignSampling = builder._alignSampling;
            }
            _otherArgs = builder._otherArgs;
        }

        public String getName() {
            return _name;
        }

        @JsonProperty("align_sampling")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Nullable
        public Boolean getAlignSampling() {
            return _alignSampling;
        }

        @JsonInclude(value = JsonInclude.Include.NON_NULL)
        public Sampling getSampling() {
            return _sampling;
        }

        @JsonAnyGetter
        protected ImmutableMap<String, Object> getOtherArgs() {
            return _otherArgs;
        }

        private final String _name;
        private final Boolean _alignSampling;
        private final Sampling _sampling;
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
            public Builder setSampling(final Sampling value) {
                _sampling = value;
                return this;
            }

            /**
             * Adds an "unknown" arg. Optional.
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

            /**
             * Sets the "unknown" args. Optional.
             *
             * @param value the args map
             * @return this {@link Builder}
             */
            public Builder setOtherArgs(final ImmutableMap<String, Object> value) {
                _otherArgs = value;
                return this;
            }

            @NotNull
            @NotEmpty
            private String _name;
            private Boolean _alignSampling = true;
            private Sampling _sampling = new Sampling.Builder().build();
            @NotNull
            private ImmutableMap<String, Object> _otherArgs = ImmutableMap.of();
        }
    }

    /**
     * Model class to represent the sampling field of an aggregator.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Sampling {
        public String getUnit() {
            return _unit;
        }

        public int getValue() {
            return _value;
        }

        private Sampling(final Builder builder) {
            _unit = builder._unit;
            _value = builder._value;
        }

        private final String _unit;
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
             * Sets the value and unit from a {@link Period}.
             *
             * @param value a {@link Period}
             * @return this {@link Builder}
             */
            public Builder setPeriod(final Period value) {
                _value = value.toStandardSeconds().getSeconds();
                _unit = "seconds";
                return this;
            }

            @JsonProperty("value")
            @Min(1)
            private int _value = 1;
            @JsonProperty("unit")
            @NotNull
            @NotEmpty
            private String _unit = "minutes";
        }
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
