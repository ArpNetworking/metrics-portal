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
import com.arpnetworking.mql.grammar.AlertTrigger;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.sf.oval.constraint.Min;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;
import org.joda.time.DateTime;

import java.util.List;

/**
 * Model class to represent a metrics query response from KairosDB.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class MetricsQueryResponse {
    @JsonAnyGetter
    public ImmutableMap<String, Object> getOtherArgs() {
        return _otherArgs;
    }

    public ImmutableList<Query> getQueries() {
        return _queries;
    }

    private MetricsQueryResponse(final Builder builder) {
        _otherArgs = builder._otherArgs;
        _queries = builder._queries;
    }

    private final ImmutableMap<String, Object> _otherArgs;
    private final ImmutableList<Query> _queries;

    /**
     * Implementation of the builder pattern for {@link MetricsQueryResponse}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends OvalBuilder<MetricsQueryResponse> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(MetricsQueryResponse::new);
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

        /**
         * Sets the queries. Cannot be null.
         *
         * @param value the name
         * @return this {@link Builder}
         */
        public Builder setQueries(final ImmutableList<Query> value) {
            _queries = value;
            return this;
        }

        @NotNull
        private ImmutableList<Query> _queries;
        @NotNull
        private ImmutableMap<String, Object> _otherArgs = ImmutableMap.of();
    }

    /**
     * Model class to represent a query in a metrics query response.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Query {
        private Query(final Builder builder) {
            _otherArgs = builder._otherArgs;
            _sampleSize = builder._sampleSize;
            _results = builder._results;
        }

        @JsonAnyGetter
        public ImmutableMap<String, Object> getOtherArgs() {
            return _otherArgs;
        }

        @JsonProperty("sample_size")
        public long getSampleSize() {
            return _sampleSize;
        }

        public ImmutableList<QueryResult> getResults() {
            return _results;
        }

        private final ImmutableMap<String, Object> _otherArgs;
        private final long _sampleSize;
        private final ImmutableList<QueryResult> _results;

        /**
         * Implementation of the builder pattern for {@link Query}.
         *
         * @author Brandon Arp (brandon dot arp at smartsheet dot com)
         */
        public static final class Builder extends OvalBuilder<Query> {
            /**
             * Public constructor.
             */
            public Builder() {
                super(Query::new);
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

            /**
             * Sets the sample size. Required.
             *
             * @param value the sample size
             * @return this {@link Builder}
             */
            @JsonProperty("sample_size")
            public Builder setSampleSize(final long value) {
                _sampleSize = value;
                return this;
            }

            /**
             * Sets the results object. Required.
             *
             * @param value the results
             * @return this {@link Builder}
             */
            public Builder setResults(final ImmutableList<QueryResult> value) {
                _results = value;
                return this;
            }

            @NotNull
            private ImmutableList<QueryResult> _results;
            @Min(0)
            private long _sampleSize = 0;
            @NotNull
            private ImmutableMap<String, Object> _otherArgs = ImmutableMap.of();
        }
    }

    /**
     * Model class representing the result object in a kairosdb metrics query.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class QueryResult {
        public String getName() {
            return _name;
        }

        public ImmutableList<DataPoint> getValues() {
            return _values;
        }

        public ImmutableMultimap<String, String> getTags() {
            return _tags;
        }

        public ImmutableList<AlertTrigger> getAlerts() {
            return _alerts;
        }

        @JsonAnyGetter
        public ImmutableMap<String, Object> getOtherArgs() {
            return _otherArgs;
        }

        private QueryResult(final Builder builder) {
            _otherArgs = builder._otherArgs;
            _values = builder._values;
            _alerts = builder._alerts;
            _name = builder._name;
            _tags = builder._tags;
        }

        private final ImmutableMap<String, Object> _otherArgs;
        private final ImmutableList<DataPoint> _values;
        private final ImmutableList<AlertTrigger> _alerts;
        private final String _name;
        private final ImmutableMultimap<String, String> _tags;

        /**
         * Implementation of the builder pattern for a {@link QueryResult}.
         *
         * @author Brandon Arp (brandon dot arp at smartsheet dot com)
         */
        public static final class Builder extends OvalBuilder<QueryResult> {
            /**
             * Public constructor.
             */
            public Builder() {
                super(QueryResult::new);
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

            /**
             * Set other args. Optional.
             *
             * @param value value for the other args
             * @return this {@link Builder}
             */
            @JsonAnySetter
            public Builder setOtherArgs(final ImmutableMap<String, Object> value) {
                _otherArgs = value;
                return this;
            }

            /**
             * Sets the values list. Optional. Cannot be null.
             *
             * @param value the values
             * @return this {@link Builder}
             */
            public Builder setValues(final ImmutableList<DataPoint> value) {
                _values = value;
                return this;
            }

            /**
             * Sets the name. Required. Cannot be null or empty.
             *
             * @param value the name of the metric
             * @return this {@link Builder}
             */
            public Builder setName(final String value) {
                _name = value;
                return this;
            }

            /**
             * Sets the tags. Required. Cannot be null or empty.
             *
             * @param value the tags
             * @return this {@link Builder}
             */
            public Builder setTags(final ImmutableMultimap<String, String> value) {
                _tags = value;
                return this;
            }

            /**
             * Sets the alerts. Optional. Cannot be null. Defaults to empty.
             *
             * @param value the alerts
             * @return this {@link Builder}
             */
            public Builder setAlerts(final ImmutableList<AlertTrigger> value) {
                _alerts = value;
                return this;
            }

            @NotNull
            @NotEmpty
            private String _name;
            @NotNull
            private ImmutableList<DataPoint> _values = ImmutableList.of();
            @NotNull
            private ImmutableMap<String, Object> _otherArgs = ImmutableMap.of();
            @NotNull
            private ImmutableList<AlertTrigger> _alerts = ImmutableList.of();
            @NotNull
            @NotEmpty
            private ImmutableMultimap<String, String> _tags = ImmutableMultimap.of();
        }
    }

    /**
     * Model class for a data point in a kairosdb metrics query.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class DataPoint {
        public DateTime getTime() {
            return _time;
        }

        public Object getValue() {
            return _value;
        }

        @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Jackson
        @JsonValue
        private ImmutableList<Object> serialize() {
            return ImmutableList.of(_time.getMillis(), _value);
        }

        private DataPoint(final Builder builder) {
            _time = builder._time;
            _value = builder._value;
        }

        private final DateTime _time;
        private final Object _value;

        /**
         * Implementation of the builder pattern for a {@link DataPoint}.
         *
         * @author Brandon Arp (brandon dot arp at smartsheet dot com)
         */
        public static final class Builder extends OvalBuilder<DataPoint> {
            /**
             * Public constructor.
             */
            public Builder() {
                super(DataPoint::new);
            }

            /**
             * Public constructor.
             *
             * @param arr a 2-element {@link List} containing the time and value at that time.
             */
            @JsonCreator
            public Builder(final List<Object> arr) {
                super(DataPoint::new);
                final long timestamp = (long) arr.get(0);
                _time = new DateTime(timestamp);
                _value = arr.get(1);
            }

            /**
             * Sets the time. Required. Cannot be null.
             *
             * @param value the time
             * @return this {@link Builder}
             */
            public Builder setTime(final DateTime value) {
                _time = value;
                return this;
            }

            /**
             * Sets the value. Required. Cannot be null.
             *
             * @param value the value
             * @return this {@link Builder}
             */
            public Builder setValue(final Object value) {
                _value = value;
                return this;
            }

            @NotNull
            private DateTime _time;
            @NotNull
            private Object _value;
        }
    }
}
