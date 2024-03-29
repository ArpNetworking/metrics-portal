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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;
import net.sf.oval.constraint.ValidateWithMethod;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Model class to represent a metrics query.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
@Loggable
public final class MetricsQuery {
    /**
     * Gets the relative start time of the query (inclusive). Only one of
     * {@link #getStartTimeRelative()} and {@link #getStartTimeMillis()} is set to a
     * non-empty value.
     *
     * @return the start time in milliseconds
     */
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    @JsonProperty("start_relative")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Optional<RelativeDateTime> getStartTimeRelative() {
        return _startTimeRelative;
    }

    /**
     * Gets the start time of the query in epoch milliseconds (inclusive).
     *
     * @return the start time in milliseconds
     */
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    @JsonProperty("start_absolute")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Optional<Long> getStartTimeMillis() {
        return _startTime.map(Instant::toEpochMilli);
    }

    /**
     * Gets the end time of the query in epoch milliseconds (inclusive).
     *
     * @return the end time in milliseconds
     */
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    @JsonProperty("end_absolute")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Optional<Long> getEndTimeMillis() {
        return _endTime.map(Instant::toEpochMilli);
    }

    /**
     * Gets the relative end time of the query (inclusive). Only up to
     * one of {@link #getEndTimeRelative()} and {@link #getEndTimeMillis()}
     * is set to a non-empty value. If both return an empty value then the
     * end date is assumed to be the current date and time.
     *
     * @return the start time in milliseconds
     */
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    @JsonProperty("end_relative")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Optional<RelativeDateTime> getEndTimeRelative() {
        return _endTimeRelative;
    }

    @JsonIgnore
    public Optional<Instant> getStartTime() {
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
    public ImmutableMap<String, Object> getOtherArgs() {
        return _otherArgs;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("startTime", _startTime)
                .add("endTime", _endTime)
                .add("startTimeRelative", _startTimeRelative)
                .add("endTimeRelative", _endTimeRelative)
                .add("metrics", _metrics)
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
        final MetricsQuery that = (MetricsQuery) o;
        return Objects.equals(_startTime, that._startTime)
                && Objects.equals(_endTime, that._endTime)
                && Objects.equals(_startTimeRelative, that._startTimeRelative)
                && Objects.equals(_endTimeRelative, that._endTimeRelative)
                && Objects.equals(_metrics, that._metrics)
                && Objects.equals(_otherArgs, that._otherArgs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_startTime, _endTime, _startTimeRelative, _endTimeRelative, _metrics, _otherArgs);
    }

    private MetricsQuery(final Builder builder) {
        _startTime = Optional.ofNullable(builder._startTime);
        _endTime = Optional.ofNullable(builder._endTime);
        _startTimeRelative = Optional.ofNullable(builder._startRelative);
        _endTimeRelative = Optional.ofNullable(builder._endRelative);
        _metrics = builder._metrics;
        _otherArgs = ImmutableMap.copyOf(builder._otherArgs);
    }

    private final Optional<Instant> _startTime;
    private final Optional<Instant> _endTime;
    private final Optional<RelativeDateTime> _startTimeRelative;
    private final Optional<RelativeDateTime> _endTimeRelative;
    private final ImmutableList<Metric> _metrics;
    private final ImmutableMap<String, Object> _otherArgs;

    /**
     * Implementation of the builder pattern for {@link MetricsQuery}.
     */
    public static final class Builder extends ThreadLocalBuilder<MetricsQuery> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(MetricsQuery::new);
        }

        /**
         * Sets the start time of the query. This is a convenience method
         * for {@link #setStartTimeMillis(Long)}. Start time must be set
         * with one of these:
         *
         * <ul>
         *     <li>{@link #setStartTime(Instant)}</li>
         *     <li>{@link #setStartTimeMillis(Long)}</li>
         *     <li>{@link #setStartTimeRelative(RelativeDateTime)}</li>
         * </ul>
         *
         * The first two set an absolute start time while the third sets
         * a relative start time.
         *
         * @param value the start time
         * @return this {@link Builder}
         */
        public Builder setStartTime(@Nullable final Instant value) {
            _startTime = value;
            return this;
        }

        /**
         * Sets the absolute start time in milliseconds. Start time must be set
         * with one of these:
         *
         * <ul>
         *     <li>{@link #setStartTime(Instant)}</li>
         *     <li>{@link #setStartTimeMillis(Long)}</li>
         *     <li>{@link #setStartTimeRelative(RelativeDateTime)}</li>
         * </ul>
         *
         * The first two set an absolute start time while the third sets
         * a relative start time.
         *
         * @param millis the start time in milliseconds
         * @return this {@link Builder}
         */
        @JsonProperty("start_absolute")
        public Builder setStartTimeMillis(@Nullable final Long millis) {
            _startTime = millis == null ? null : Instant.ofEpochMilli(millis);
            return this;
        }

        /**
         * Sets the relative start time. Start time must be set
         * with one of these:
         *
         * <ul>
         *     <li>{@link #setStartTime(Instant)}</li>
         *     <li>{@link #setStartTimeMillis(Long)}</li>
         *     <li>{@link #setStartTimeRelative(RelativeDateTime)}</li>
         * </ul>
         *
         * The first two set an absolute start time while the third sets
         * a relative start time.
         *
         * @param value the relative start time
         * @return this {@link Builder}
         */
        @JsonProperty("start_relative")
        public Builder setStartTimeRelative(@Nullable final RelativeDateTime value) {
            _startRelative = value;
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
        public Builder setEndTimeMillis(@Nullable final Long millis) {
            _endTime = millis == null ? null : Instant.ofEpochMilli(millis);
            return this;
        }

        /**
         * Sets the relative end time. End time is optional and effectively defaults
         * to the current date and time. Setting end time can only be done with up to
         * one of these:
         *
         * <ul>
         *     <li>{@link #setEndTime(Instant)}</li>
         *     <li>{@link #setEndTimeMillis(Long)}</li>
         *     <li>{@link #setEndTimeRelative(RelativeDateTime)}</li>
         * </ul>
         *
         * The first two set an absolute end time while the third sets
         * a relative end time.
         *
         * @param value the relative end time
         * @return this {@link Builder}
         */
        @JsonProperty("end_relative")
        public Builder setEndTimeRelative(@Nullable final RelativeDateTime value) {
            _endRelative = value;
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
            _startTime = null;
            _endTime = null;
            _startRelative = null;
            _endRelative = null;
            _metrics = ImmutableList.of();
            _otherArgs = Maps.newHashMap();
        }

        @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "Method is called by Oval")
        private boolean validateStart(@Nullable final Instant ignored) {
            if (_startTime == null) {
                return _startRelative != null;
            } else {
                return _startRelative == null;
            }
        }

        @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "Method is called by Oval")
        private boolean validateEnd(@Nullable final Instant ignored) {
            if (_endTime != null) {
                return _endRelative == null;
            }
            return true;
        }

        @ValidateWithMethod(methodName = "validateStart", parameterType = Instant.class,
                message = "Must set either start time or start relative time, but not both.")
        private Instant _startTime;
        @ValidateWithMethod(methodName = "validateEnd", parameterType = Instant.class,
                message = "Must set either end time or end relative time, but not both.")
        private Instant _endTime;
        private RelativeDateTime _startRelative;
        private RelativeDateTime _endRelative;
        @NotNull
        @NotEmpty
        private ImmutableList<Metric> _metrics = ImmutableList.of();
        @NotNull
        private Map<String, Object> _otherArgs = Maps.newHashMap();
    }

    /**
     * Model for the group_by fields in the {@link Metric}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "name")
    @JsonSubTypes({
            @JsonSubTypes.Type(name = "bin", value = QueryBinGroupBy.class),
            @JsonSubTypes.Type(name = "tag", value = QueryTagGroupBy.class),
            @JsonSubTypes.Type(name = "time", value = QueryTimeGroupBy.class),
            @JsonSubTypes.Type(name = "type", value = QueryTypeGroupBy.class),
            @JsonSubTypes.Type(name = "value", value = QueryValueGroupBy.class)})
    public abstract static class QueryGroupBy {

        private QueryGroupBy(final QueryGroupBy.Builder<?, ?> builder) {
        }

        /**
         * Implementation of the builder pattern for a {@link QueryGroupBy}.
         *
         * @param <B> type of the builder
         * @param <T> type of the thing to be built
         * @author Brandon Arp (brandon dot arp at smartsheet dot com)
         */
        public abstract static class Builder<B extends QueryGroupBy.Builder<B, T>, T extends QueryGroupBy>
                extends ThreadLocalBuilder<T> {

            /**
             * Protected constructor.
             *
             * @param targetConstructor the constructor for the {@link QueryGroupBy}
             * @param <B> Type of the builder
             */
            protected <B extends com.arpnetworking.commons.builder.Builder<T>> Builder(final Function<B, T> targetConstructor) {
                super(targetConstructor);
            }

            /**
             * Gets the instance of the {@link QueryGroupBy.Builder} with the proper type.
             *
             * @return this {@link QueryGroupBy.Builder}
             */
            protected abstract B self();
        }
    }

    /**
     * Model for the group_by fields of type "bin" in the {@link Metric}.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
     */
    @Loggable
    public static final class QueryBinGroupBy extends QueryGroupBy {
        public ImmutableList<Number> getBins() {
            return _bins;
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
            final QueryBinGroupBy otherQueryBinGroupBy = (QueryBinGroupBy) o;
            return Objects.equals(_bins, otherQueryBinGroupBy._bins)
                    && Objects.equals(_otherArgs, otherQueryBinGroupBy._otherArgs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_bins, _otherArgs);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("bins", _bins)
                    .add("otherArgs", _otherArgs)
                    .toString();
        }

        private QueryBinGroupBy(final QueryBinGroupBy.Builder builder) {
            super(builder);
            _bins = builder._bins;
            _otherArgs = ImmutableMap.copyOf(builder._otherArgs);
        }

        private final ImmutableList<Number> _bins;
        private final ImmutableMap<String, Object> _otherArgs;

        /**
         * Implementation of the builder pattern for a {@link QueryBinGroupBy}.
         *
         * @author Brandon Arp (brandon dot arp at smartsheet dot com)
         */
        public static final class Builder extends QueryGroupBy.Builder<QueryBinGroupBy.Builder, QueryBinGroupBy> {
            /**
             * Public constructor.
             */
            public Builder() {
                super(QueryBinGroupBy::new);
            }

            /**
             * Sets the bins. Required. Cannot be null or empty.
             *
             * @param value the bins
             * @return this {@link QueryBinGroupBy.Builder}
             */
            public QueryBinGroupBy.Builder setBins(final ImmutableList<Number> value) {
                _bins = value;
                return self();
            }

            /**
             * Adds an attribute not explicitly modeled by this class. Optional.
             *
             * @param key the attribute name
             * @param value the attribute value
             * @return this {@link QueryBinGroupBy.Builder}
             */
            @JsonAnySetter
            public QueryBinGroupBy.Builder addOtherArg(final String key, final Object value) {
                _otherArgs.put(key, value);
                return this;
            }

            /**
             * Sets the attributes not explicitly modeled by this class. Optional.
             *
             * @param value the other attributes
             * @return this {@link QueryBinGroupBy.Builder}
             */
            @JsonIgnore
            public QueryBinGroupBy.Builder setOtherArgs(final ImmutableMap<String, Object> value) {
                _otherArgs = value;
                return this;
            }

            /**
             * Gets the instance of the {@link QueryBinGroupBy.Builder} with the proper type.
             *
             * @return this {@link QueryBinGroupBy.Builder}
             */
            protected QueryBinGroupBy.Builder self() {
                return this;
            }

            @Override
            public void reset() {
                _bins = null;
                _otherArgs = Maps.newHashMap();
            }

            @NotNull
            @NotEmpty
            private ImmutableList<Number> _bins;
            @NotNull
            private Map<String, Object> _otherArgs = Maps.newHashMap();
        }
    }

    /**
     * Model for the group_by fields of type "tag" in the {@link Metric}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    @Loggable
    public static final class QueryTagGroupBy extends QueryGroupBy {
        public ImmutableSet<String> getTags() {
            return _tags;
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
            final QueryTagGroupBy otherQueryTagGroupBy = (QueryTagGroupBy) o;
            return Objects.equals(_tags, otherQueryTagGroupBy._tags)
                    && Objects.equals(_otherArgs, otherQueryTagGroupBy._otherArgs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_tags, _otherArgs);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("type", _tags)
                    .add("otherArgs", _otherArgs)
                    .toString();
        }

        private QueryTagGroupBy(final QueryTagGroupBy.Builder builder) {
            super(builder);
            _tags = builder._tags;
            _otherArgs = ImmutableMap.copyOf(builder._otherArgs);
        }

        private final ImmutableSet<String> _tags;
        private final ImmutableMap<String, Object> _otherArgs;

        /**
         * Implementation of the builder pattern for a {@link QueryTagGroupBy}.
         *
         * @author Brandon Arp (brandon dot arp at smartsheet dot com)
         */
        public static final class Builder extends QueryGroupBy.Builder<QueryTagGroupBy.Builder, QueryTagGroupBy> {
            /**
             * Public constructor.
             */
            public Builder() {
                super(QueryTagGroupBy::new);
            }

            /**
             * Sets the tags. Required. Cannot be null or empty.
             *
             * @param value the tags
             * @return this {@link QueryTagGroupBy.Builder}
             */
            public QueryTagGroupBy.Builder setTags(final ImmutableSet<String> value) {
                _tags = value;
                return self();
            }

            /**
             * Adds an attribute not explicitly modeled by this class. Optional.
             *
             * @param key the attribute name
             * @param value the attribute value
             * @return this {@link QueryTagGroupBy.Builder}
             */
            @JsonAnySetter
            public QueryTagGroupBy.Builder addOtherArg(final String key, final Object value) {
                _otherArgs.put(key, value);
                return this;
            }

            /**
             * Sets the attributes not explicitly modeled by this class. Optional.
             *
             * @param value the other attributes
             * @return this {@link QueryTagGroupBy.Builder}
             */
            @JsonIgnore
            public QueryTagGroupBy.Builder setOtherArgs(final ImmutableMap<String, Object> value) {
                _otherArgs = value;
                return this;
            }

            /**
             * Gets the instance of the {@link QueryTagGroupBy.Builder} with the proper type.
             *
             * @return this {@link QueryTagGroupBy.Builder}
             */
            protected QueryTagGroupBy.Builder self() {
                return this;
            }

            @Override
            public void reset() {
                _tags = null;
                _otherArgs = Maps.newHashMap();
            }

            @NotNull
            @NotEmpty
            private ImmutableSet<String> _tags;
            @NotNull
            private Map<String, Object> _otherArgs = Maps.newHashMap();
        }
    }

    /**
     * Model for the group_by fields of type "time" in the {@link Metric}.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
     */
    @Loggable
    public static final class QueryTimeGroupBy extends QueryGroupBy {
        @JsonProperty("group_count")
        public int getGroupCount() {
            return _groupCount;
        }

        @JsonProperty("range_size")
        public RelativeDateTime getRangeSize() {
            return _rangeSize;
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
            final QueryTimeGroupBy otherQueryTimeGroupBy = (QueryTimeGroupBy) o;
            return _groupCount == otherQueryTimeGroupBy._groupCount
                    && Objects.equals(_rangeSize, otherQueryTimeGroupBy._rangeSize)
                    && Objects.equals(_otherArgs, otherQueryTimeGroupBy._otherArgs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_groupCount, _rangeSize, _otherArgs);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("groupCount", _groupCount)
                    .add("rangeSize", _rangeSize)
                    .add("otherArgs", _otherArgs)
                    .toString();
        }

        private QueryTimeGroupBy(final Builder builder) {
            super(builder);
            _groupCount = builder._groupCount;
            _rangeSize = builder._rangeSize;
            _otherArgs = ImmutableMap.copyOf(builder._otherArgs);
        }

        private final int _groupCount;
        private final RelativeDateTime _rangeSize;
        private final ImmutableMap<String, Object> _otherArgs;

        /**
         * Implementation of the builder pattern for a {@link QueryTimeGroupBy}.
         *
         * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
         */
        public static final class Builder extends QueryGroupBy.Builder<Builder, QueryTimeGroupBy> {
            /**
             * Public constructor.
             */
            public Builder() {
                super(QueryTimeGroupBy::new);
            }

            /**
             * Sets the group count. Required. Cannot be null.
             *
             * @param value the group count
             * @return this {@link Builder}
             */
            @JsonProperty("group_count")
            public Builder setGroupCount(final Integer value) {
                _groupCount = value;
                return self();
            }

            /**
             * Sets the range size. Required. Cannot be null.
             *
             * @param value the range size
             * @return this {@link Builder}
             */
            @JsonProperty("range_size")
            public Builder setRangeSize(final RelativeDateTime value) {
                _rangeSize = value;
                return self();
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

            /**
             * Gets the instance of the {@link Builder} with the proper type.
             *
             * @return this {@link Builder}
             */
            protected Builder self() {
                return this;
            }

            @Override
            public void reset() {
                _groupCount = null;
                _rangeSize = null;
                _otherArgs = Maps.newHashMap();
            }

            @NotNull
            private Integer _groupCount;
            @NotNull
            private RelativeDateTime _rangeSize;
            @NotNull
            private Map<String, Object> _otherArgs = Maps.newHashMap();
        }
    }

    /**
     * Model for the group_by fields of type "type" in the {@link Metric}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    @Loggable
    public static final class QueryTypeGroupBy extends QueryGroupBy {
        public String getType() {
            return _type;
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
            final QueryTypeGroupBy otherQueryTypeGroupBy = (QueryTypeGroupBy) o;
            return Objects.equals(_type, otherQueryTypeGroupBy._type)
                    && Objects.equals(_otherArgs, otherQueryTypeGroupBy._otherArgs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_type, _otherArgs);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("type", _type)
                    .add("value", _otherArgs)
                    .toString();
        }

        private QueryTypeGroupBy(final QueryTypeGroupBy.Builder builder) {
            super(builder);
            _type = builder._type;
            _otherArgs =  ImmutableMap.copyOf(builder._otherArgs);
        }

        private final String _type;
        private final ImmutableMap<String, Object> _otherArgs;

        /**
         * Implementation of the builder pattern for a {@link QueryTypeGroupBy}.
         *
         * @author Brandon Arp (brandon dot arp at smartsheet dot com)
         */
        public static final class Builder extends QueryGroupBy.Builder<QueryTypeGroupBy.Builder, QueryTypeGroupBy> {
            /**
             * Public constructor.
             */
            public Builder() {
                super(QueryTypeGroupBy::new);
            }

            /**
             * Sets the type. Required. Cannot be null or empty.
             *
             * @param value the type
             * @return this {@link QueryTypeGroupBy.Builder}
             */
            public QueryTypeGroupBy.Builder setType(final String value) {
                _type = value;
                return self();
            }

            /**
             * Adds an attribute not explicitly modeled by this class. Optional.
             *
             * @param key the attribute name
             * @param value the attribute value
             * @return this {@link MetricsQueryResponse.QueryTypeGroupBy.Builder}
             */
            @JsonAnySetter
            public QueryTypeGroupBy.Builder addOtherArg(final String key, final Object value) {
                _otherArgs.put(key, value);
                return this;
            }

            /**
             * Sets the attributes not explicitly modeled by this class. Optional.
             *
             * @param value the other attributes
             * @return this {@link MetricsQueryResponse.QueryTypeGroupBy.Builder}
             */
            @JsonIgnore
            public QueryTypeGroupBy.Builder setOtherArgs(final ImmutableMap<String, Object> value) {
                _otherArgs = value;
                return this;
            }

            /**
             * Gets the instance of the {@link QueryTypeGroupBy.Builder} with the proper type.
             *
             * @return this {@link QueryTypeGroupBy.Builder}
             */
            protected QueryTypeGroupBy.Builder self() {
                return this;
            }

            @Override
            protected void reset() {
                _type = null;
                _otherArgs = Maps.newHashMap();
            }

            @NotNull
            @NotEmpty
            private String _type;
            @NotNull
            private Map<String, Object> _otherArgs = Maps.newHashMap();
        }
    }

    /**
     * Model for the group_by fields of type "value" in the {@link Metric}.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
     */
    @Loggable
    public static final class QueryValueGroupBy extends QueryGroupBy {
        @JsonProperty("range_size")
        public Number getRangeSize() {
            return _rangeSize;
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
            final QueryValueGroupBy otherQueryValueGroupBy = (QueryValueGroupBy) o;
            return Objects.equals(_rangeSize, otherQueryValueGroupBy._rangeSize)
                    && Objects.equals(_otherArgs, otherQueryValueGroupBy._otherArgs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_rangeSize, _otherArgs);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("rangeSize", _rangeSize)
                    .add("otherArgs", _otherArgs)
                    .toString();
        }

        private QueryValueGroupBy(final Builder builder) {
            super(builder);
            _rangeSize = builder._rangeSize;
            _otherArgs = ImmutableMap.copyOf(builder._otherArgs);
        }

        private final Number _rangeSize;
        private final ImmutableMap<String, Object> _otherArgs;

        /**
         * Implementation of the builder pattern for a {@link QueryValueGroupBy}.
         *
         * @author Brandon Arp (brandon dot arp at smartsheet dot com)
         */
        public static final class Builder extends QueryGroupBy.Builder<Builder, QueryValueGroupBy> {
            /**
             * Public constructor.
             */
            public Builder() {
                super(QueryValueGroupBy::new);
            }

            /**
             * Sets the range size. Required. Cannot be null.
             *
             * @param value the range size
             * @return this {@link Builder}
             */
            @JsonProperty("range_size")
            public Builder setRangeSize(final Number value) {
                _rangeSize = value;
                return self();
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

            /**
             * Gets the instance of the {@link Builder} with the proper type.
             *
             * @return this {@link Builder}
             */
            protected Builder self() {
                return this;
            }

            @Override
            public void reset() {
                _rangeSize = null;
                _otherArgs = Maps.newHashMap();
            }

            @NotNull
            private Number _rangeSize;
            @NotNull
            private Map<String, Object> _otherArgs = Maps.newHashMap();
        }
    }
}
