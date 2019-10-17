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
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Maps;
import models.internal.TimeSeriesResult;
import models.internal.impl.DefaultTimeSeriesResult;
import net.sf.oval.constraint.Min;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Model class to represent a metrics query response from KairosDB.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class MetricsQueryResponse {

    public ImmutableList<Query> getQueries() {
        return _queries;
    }

    @JsonAnyGetter
    public ImmutableMap<String, Object> getOtherArgs() {
        return _otherArgs;
    }

    /**
     * Converts this KairosDB model to an internal TimeSeriesResult model.
     *
     * @return a new {@link TimeSeriesResult} model
     */
    public TimeSeriesResult toTimeSeriesResult() {
        return new DefaultTimeSeriesResult.Builder()
                .setQueries(_queries.stream().map(Query::toInternal).collect(ImmutableList.toImmutableList()))
                .setOtherArgs(_otherArgs)
                .build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MetricsQueryResponse otherMetricsQueryResponse = (MetricsQueryResponse) o;
        return Objects.equals(_queries, otherMetricsQueryResponse._queries)
                && Objects.equals(_otherArgs, otherMetricsQueryResponse._otherArgs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_queries, _otherArgs);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("queries", _queries)
                .add("otherArgs", _otherArgs)
                .toString();
    }

    private MetricsQueryResponse(final Builder builder) {
        _queries = builder._queries;
        _otherArgs = ImmutableMap.copyOf(builder._otherArgs);
    }

    private final ImmutableList<Query> _queries;
    private final ImmutableMap<String, Object> _otherArgs;

    /**
     * Implementation of the builder pattern for {@link MetricsQueryResponse}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends ThreadLocalBuilder<MetricsQueryResponse> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(MetricsQueryResponse::new);
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
            _queries = null;
            _otherArgs = Maps.newHashMap();
        }

        @NotNull
        private ImmutableList<Query> _queries;
        @NotNull
        private Map<String, Object> _otherArgs = Maps.newHashMap();
    }

    /**
     * Model class to represent a query in a metrics query response.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Query {
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

        /**
         * Converts this model to an internal model.
         *
         * @return a new internal model
         */
        public TimeSeriesResult.Query toInternal() {
            return new DefaultTimeSeriesResult.Query.Builder()
                    .setSampleSize(_sampleSize)
                    .setOtherArgs(_otherArgs)
                    .setResults(_results.stream().map(QueryResult::toInternal).collect(ImmutableList.toImmutableList()))
                    .build();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Query otherQuery = (Query) o;
            return Objects.equals(_sampleSize, otherQuery._sampleSize)
                    && Objects.equals(_results, otherQuery._results)
                    && Objects.equals(_otherArgs, otherQuery._otherArgs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_sampleSize, _results, _otherArgs);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("sampleSize", _sampleSize)
                    .add("results", _results)
                    .add("otherArgs", _otherArgs)
                    .toString();
        }

        private Query(final Builder builder) {
            _sampleSize = builder._sampleSize;
            _results = builder._results;
            _otherArgs = ImmutableMap.copyOf(builder._otherArgs);
        }

        private final long _sampleSize;
        private final ImmutableList<QueryResult> _results;
        private final ImmutableMap<String, Object> _otherArgs;

        /**
         * Implementation of the builder pattern for {@link Query}.
         *
         * @author Brandon Arp (brandon dot arp at smartsheet dot com)
         */
        public static final class Builder extends ThreadLocalBuilder<Query> {
            /**
             * Public constructor.
             */
            public Builder() {
                super(Query::new);
            }

            /**
             * Sets the sample size. Required.
             *
             * @param value the sample size
             * @return this {@link Builder}
             */
            @JsonProperty("sample_size")
            public Builder setSampleSize(final Long value) {
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
                _results = null;
                _sampleSize = 0L;
                _otherArgs = Maps.newHashMap();
            }

            @NotNull
            private ImmutableList<QueryResult> _results;
            @Min(0)
            @NotNull
            private Long _sampleSize = 0L;
            @NotNull
            private Map<String, Object> _otherArgs = Maps.newHashMap();
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

        @JsonProperty("group_by")
        public ImmutableList<QueryGroupBy> getGroupBy() {
            return _groupBy;
        }

        @JsonAnyGetter
        public ImmutableMap<String, Object> getOtherArgs() {
            return _otherArgs;
        }

        /**
         * Converts this model to an internal model.
         *
         * @return a new internal model
         */
        public TimeSeriesResult.Result toInternal() {
            return new DefaultTimeSeriesResult.Result.Builder()
                    .setAlerts(ImmutableList.of())
                    .setGroupBy(_groupBy.stream().map(QueryGroupBy::toInternal).collect(ImmutableList.toImmutableList()))
                    .setName(_name)
                    .setValues(_values.stream().map(DataPoint::toInternal).collect(ImmutableList.toImmutableList()))
                    .setTags(_tags)
                    .setOtherArgs(_otherArgs)
                    .build();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final QueryResult otherQueryResult = (QueryResult) o;
            return Objects.equals(_values, otherQueryResult._values)
                    && Objects.equals(_name, otherQueryResult._name)
                    && Objects.equals(_tags, otherQueryResult._tags)
                    && Objects.equals(_groupBy, otherQueryResult._groupBy)
                    && Objects.equals(_otherArgs, otherQueryResult._otherArgs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_values, _name, _tags, _groupBy, _otherArgs);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("values", _values)
                    .add("name", _name)
                    .add("tags", _tags)
                    .add("groupBy", _groupBy)
                    .add("otherArgs", _otherArgs)
                    .toString();
        }

        private QueryResult(final Builder builder) {
            _values = builder._values;
            _name = builder._name;
            _tags = builder._tags;
            _groupBy = builder._groupBy;
            _otherArgs = ImmutableMap.copyOf(builder._otherArgs);
        }

        private final ImmutableList<DataPoint> _values;
        private final String _name;
        private final ImmutableMultimap<String, String> _tags;
        private final ImmutableList<QueryGroupBy> _groupBy;
        private final ImmutableMap<String, Object> _otherArgs;

        /**
         * Implementation of the builder pattern for a {@link QueryResult}.
         *
         * @author Brandon Arp (brandon dot arp at smartsheet dot com)
         */
        public static final class Builder extends ThreadLocalBuilder<QueryResult> {
            /**
             * Public constructor.
             */
            public Builder() {
                super(QueryResult::new);
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
             * Sets the group by. Optional. Cannot be null.
             *
             * @param value the group by list
             * @return this {@link Builder}
             */
            @JsonProperty("group_by")
            public Builder setGroupBy(final ImmutableList<QueryGroupBy> value) {
                _groupBy = value;
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
            public void reset() {
                _name = null;
                _values = ImmutableList.of();
                _tags = ImmutableMultimap.of();
                _groupBy = ImmutableList.of();
                _otherArgs = Maps.newHashMap();
            }

            @NotNull
            @NotEmpty
            private String _name;
            @NotNull
            private ImmutableList<DataPoint> _values = ImmutableList.of();
            @NotNull
            private ImmutableMultimap<String, String> _tags = ImmutableMultimap.of();
            @NotNull
            private ImmutableList<QueryGroupBy> _groupBy = ImmutableList.of();
            @NotNull
            private Map<String, Object> _otherArgs = Maps.newHashMap();
        }
    }

    /**
     * Model for the group_by fields in the {@link QueryResult}.
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
        /**
         * Converts this model to an internal model.
         *
         * @return a new internal model
         */
        public abstract TimeSeriesResult.QueryGroupBy toInternal();

        private QueryGroupBy(final Builder<?, ?> builder) {
        }

        /**
         * Implementation of the builder pattern for a {@link QueryGroupBy}.
         *
         * @param <B> type of the builder
         * @param <T> type of the thing to be built
         * @author Brandon Arp (brandon dot arp at smartsheet dot com)
         */
        public abstract static class Builder<B extends Builder<B, T>, T extends QueryGroupBy> extends ThreadLocalBuilder<T> {

            /**
             * Protected constructor.
             *
             * @param targetConstructor the constructor for the QueryGroupBy
             * @param <B> Type of the builder
             */
            protected <B extends com.arpnetworking.commons.builder.Builder<T>> Builder(final Function<B, T> targetConstructor) {
                super(targetConstructor);
            }

            /**
             * Gets the instance of the {@link Builder} with the proper type.
             *
             * @return this {@link Builder}
             */
            protected abstract B self();
        }
    }

    /**
     * Model for the group_by fields of type "bin" in the {@link QueryResult}.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
     */
    public static final class QueryBinGroupBy extends QueryGroupBy {
        public ImmutableList<Number> getBins() {
            return _bins;
        }

        public ImmutableMap<String, Integer> getGroup() {
            return _group;
        }

        @JsonAnyGetter
        public ImmutableMap<String, Object> getOtherArgs() {
            return _otherArgs;
        }

        @Override
        public TimeSeriesResult.QueryTagGroupBy toInternal() {
            throw new UnsupportedOperationException("Internal model does not support this group by");
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
                    && Objects.equals(_group, otherQueryBinGroupBy._group)
                    && Objects.equals(_otherArgs, otherQueryBinGroupBy._otherArgs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_bins, _group, _otherArgs);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("bins", _bins)
                    .add("group", _group)
                    .add("otherArgs", _otherArgs)
                    .toString();
        }

        private QueryBinGroupBy(final Builder builder) {
            super(builder);
            _bins = builder._bins;
            _group = builder._group;
            _otherArgs = ImmutableMap.copyOf(builder._otherArgs);
        }

        private final ImmutableList<Number> _bins;
        private final ImmutableMap<String, Integer> _group;
        private final ImmutableMap<String, Object> _otherArgs;

        /**
         * Implementation of the builder pattern for a {@link QueryBinGroupBy}.
         *
         * @author Brandon Arp (brandon dot arp at smartsheet dot com)
         */
        public static final class Builder extends QueryGroupBy.Builder<Builder, QueryBinGroupBy> {
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
             * @return this {@link Builder}
             */
            public Builder setBins(final ImmutableList<Number> value) {
                _bins = value;
                return self();
            }

            /**
             * Sets the group. Required. Cannot be null or empty.
             *
             * @param value the group
             * @return this {@link Builder}
             */
            public Builder setGroup(final ImmutableMap<String, Integer> value) {
                _group = value;
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
                _bins = null;
                _group = null;
                _otherArgs = Maps.newHashMap();
            }

            @NotNull
            @NotEmpty
            private ImmutableList<Number> _bins;
            @NotNull
            @NotEmpty
            private ImmutableMap<String, Integer> _group;
            @NotNull
            private Map<String, Object> _otherArgs = Maps.newHashMap();
        }
    }

    /**
     * Model for the group_by fields of type "tag" in the {@link QueryResult}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class QueryTagGroupBy extends QueryGroupBy {
        public ImmutableList<String> getTags() {
            return _tags;
        }

        public ImmutableMap<String, String> getGroup() {
            return _group;
        }

        @JsonAnyGetter
        public ImmutableMap<String, Object> getOtherArgs() {
            return _otherArgs;
        }

        @Override
        public TimeSeriesResult.QueryTagGroupBy toInternal() {
            return new DefaultTimeSeriesResult.QueryTagGroupBy.Builder()
                    .setGroup(_group)
                    .setTags(_tags)
                    .build();
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
                    && Objects.equals(_group, otherQueryTagGroupBy._group)
                    && Objects.equals(_otherArgs, otherQueryTagGroupBy._otherArgs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_tags, _group, _otherArgs);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("tags", _tags)
                    .add("group", _group)
                    .add("otherArgs", _otherArgs)
                    .toString();
        }

        private QueryTagGroupBy(final Builder builder) {
            super(builder);
            _tags = builder._tags;
            _group = builder._group;
            _otherArgs = ImmutableMap.copyOf(builder._otherArgs);
        }

        private final ImmutableList<String> _tags;
        private final ImmutableMap<String, String> _group;
        private final ImmutableMap<String, Object> _otherArgs;

        /**
         * Implementation of the builder pattern for a {@link QueryTagGroupBy}.
         *
         * @author Brandon Arp (brandon dot arp at smartsheet dot com)
         */
        public static final class Builder extends QueryGroupBy.Builder<Builder, QueryTagGroupBy> {
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
             * @return this {@link Builder}
             */
            public Builder setTags(final ImmutableList<String> value) {
                _tags = value;
                return self();
            }

            /**
             * Sets the group. Required. Cannot be null or empty.
             *
             * @param value the group
             * @return this {@link Builder}
             */
            public Builder setGroup(final ImmutableMap<String, String> value) {
                _group = value;
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
                _tags = null;
                _group = null;
                _otherArgs = Maps.newHashMap();
            }

            @NotNull
            @NotEmpty
            private ImmutableList<String> _tags;
            @NotNull
            @NotEmpty
            private ImmutableMap<String, String> _group;
            @NotNull
            private Map<String, Object> _otherArgs = Maps.newHashMap();
        }
    }

    /**
     * Model for the group_by fields of type "time" in the {@link QueryResult}.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
     */
    public static final class QueryTimeGroupBy extends QueryGroupBy {
        @JsonProperty("group_count")
        public int getGroupCount() {
            return _groupCount;
        }

        @JsonProperty("range_size")
        public RelativeDateTime getRangeSize() {
            return _rangeSize;
        }

        public ImmutableMap<String, Integer> getGroup() {
            return _group;
        }

        @JsonAnyGetter
        public ImmutableMap<String, Object> getOtherArgs() {
            return _otherArgs;
        }

        @Override
        public TimeSeriesResult.QueryTagGroupBy toInternal() {
            throw new UnsupportedOperationException("Internal model does not support this group by");
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
                    && Objects.equals(_group, otherQueryTimeGroupBy._group)
                    && Objects.equals(_otherArgs, otherQueryTimeGroupBy._otherArgs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_groupCount, _rangeSize, _group, _otherArgs);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("groupCount", _groupCount)
                    .add("rangeSize", _rangeSize)
                    .add("group", _group)
                    .add("otherArgs", _otherArgs)
                    .toString();
        }

        private QueryTimeGroupBy(final Builder builder) {
            super(builder);
            _groupCount = builder._groupCount;
            _rangeSize = builder._rangeSize;
            _group = builder._group;
            _otherArgs = ImmutableMap.copyOf(builder._otherArgs);
        }

        private final int _groupCount;
        private final RelativeDateTime _rangeSize;
        private final ImmutableMap<String, Integer> _group;
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
             * Sets the group. Required. Cannot be null or empty.
             *
             * @param value the group
             * @return this {@link Builder}
             */
            public Builder setGroup(final ImmutableMap<String, Integer> value) {
                _group = value;
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
                _group = null;
                _otherArgs = Maps.newHashMap();
            }

            @NotNull
            private Integer _groupCount;
            @NotNull
            private RelativeDateTime _rangeSize;
            @NotNull
            @NotEmpty
            private ImmutableMap<String, Integer> _group;
            @NotNull
            private Map<String, Object> _otherArgs = Maps.newHashMap();
        }
    }

    /**
     * Model for the group_by fields of type "type" in the {@link QueryResult}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class QueryTypeGroupBy extends QueryGroupBy {
        public String getType() {
            return _type;
        }

        @JsonAnyGetter
        public ImmutableMap<String, Object> getOtherArgs() {
            return _otherArgs;
        }

        @Override
        public TimeSeriesResult.QueryTypeGroupBy toInternal() {
            return new DefaultTimeSeriesResult.QueryTypeGroupBy.Builder()
                    .setType(_type)
                    .build();
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

        private QueryTypeGroupBy(final Builder builder) {
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
        public static final class Builder extends QueryGroupBy.Builder<Builder, QueryTypeGroupBy> {
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
             * @return this {@link Builder}
             */
            public Builder setType(final String value) {
                _type = value;
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
     * Model for the group_by fields of type "value" in the {@link QueryResult}.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
     */
    public static final class QueryValueGroupBy extends QueryGroupBy {
        @JsonProperty("range_size")
        public Number getRangeSize() {
            return _rangeSize;
        }

        public ImmutableMap<String, Integer> getGroup() {
            return _group;
        }

        @JsonAnyGetter
        public ImmutableMap<String, Object> getOtherArgs() {
            return _otherArgs;
        }

        @Override
        public TimeSeriesResult.QueryTagGroupBy toInternal() {
            throw new UnsupportedOperationException("Internal model does not support this group by");
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
                    && Objects.equals(_group, otherQueryValueGroupBy._group)
                    && Objects.equals(_otherArgs, otherQueryValueGroupBy._otherArgs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_rangeSize, _group, _otherArgs);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("rangeSize", _rangeSize)
                    .add("group", _group)
                    .add("otherArgs", _otherArgs)
                    .toString();
        }

        private QueryValueGroupBy(final Builder builder) {
            super(builder);
            _rangeSize = builder._rangeSize;
            _group = builder._group;
            _otherArgs = ImmutableMap.copyOf(builder._otherArgs);
        }

        private final Number _rangeSize;
        private final ImmutableMap<String, Integer> _group;
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
             * Sets the group. Required. Cannot be null or empty.
             *
             * @param value the group
             * @return this {@link Builder}
             */
            public Builder setGroup(final ImmutableMap<String, Integer> value) {
                _group = value;
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
                _group = null;
                _otherArgs = Maps.newHashMap();
            }

            @NotNull
            private Number _rangeSize;
            @NotNull
            @NotEmpty
            private ImmutableMap<String, Integer> _group;
            @NotNull
            private Map<String, Object> _otherArgs = Maps.newHashMap();
        }
    }
}
