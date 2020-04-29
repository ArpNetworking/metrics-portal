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
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Holds the data for a Metric element of the metrics query.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
@Loggable
public final class Metric {
    public String getName() {
        return _name;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Multimap<String, String> getTags() {
        return _tags;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public ImmutableList<Aggregator> getAggregators() {
        return _aggregators;
    }

    @JsonProperty("group_by")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public ImmutableList<MetricsQuery.QueryGroupBy> getGroupBy() {
        return _groupBy;
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<Integer> getLimit() {
        return _limit;
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<Order> getOrder() {
        return _order;
    }

    @JsonAnyGetter
    public ImmutableMap<String, Object> getOtherArgs() {
        return _otherArgs;
    }

    private Metric(final Builder builder) {
        _name = builder._name;
        _tags = builder._tags;
        _aggregators = builder._aggregators;
        _groupBy = builder._groupBy;
        _limit = Optional.ofNullable(builder._limit);
        _order = Optional.ofNullable(builder._order);
        _otherArgs = ImmutableMap.copyOf(builder._otherArgs);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", _name)
                .add("tags", _tags)
                .add("aggregators", _aggregators)
                .add("groupBy", _groupBy)
                .add("limit", _limit)
                .add("order", _order)
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
        final Metric metric = (Metric) o;
        return Objects.equals(_name, metric._name)
                && Objects.equals(_tags, metric._tags)
                && Objects.equals(_aggregators, metric._aggregators)
                && Objects.equals(_groupBy, metric._groupBy)
                && Objects.equals(_limit, metric._limit)
                && Objects.equals(_order, metric._order)
                && Objects.equals(_otherArgs, metric._otherArgs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_name, _tags, _aggregators, _groupBy, _limit, _order, _otherArgs);
    }

    private final String _name;
    private final ImmutableMultimap<String, String> _tags;
    private final ImmutableList<Aggregator> _aggregators;
    private final ImmutableList<MetricsQuery.QueryGroupBy> _groupBy;
    private final Optional<Integer> _limit;
    private final Optional<Order> _order;
    private final ImmutableMap<String, Object> _otherArgs;

    /**
     * Implementation of the builder pattern for {@link Metric}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends ThreadLocalBuilder<Metric> {

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
        @JsonProperty("group_by")
        public Builder setGroupBy(final ImmutableList<MetricsQuery.QueryGroupBy> value) {
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
         * Sets the datapoint limit. Optional. Can be null.
         *
         * @param value the limit
         * @return this {@link Builder}
         */
        public Builder setLimit(@Nullable final Integer value) {
            _limit = value;
            return this;
        }

        /**
         * Sets the time order for returned datapoints. Optional. Can be null.
         *
         * @param value the time order to return datapoints by
         * @return this {@link Builder}
         */
        public Builder setOrder(@Nullable final Order value) {
            _order = value;
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
            _name = null;
            _aggregators = ImmutableList.of();
            _groupBy = ImmutableList.of();
            _tags = ImmutableMultimap.of();
            _limit = null;
            _order = null;
            _otherArgs = Maps.newHashMap();
        }

        @NotNull
        @NotEmpty
        private String _name;
        @NotNull
        private ImmutableList<Aggregator> _aggregators = ImmutableList.of();
        @NotNull
        private ImmutableList<MetricsQuery.QueryGroupBy> _groupBy = ImmutableList.of();
        @NotNull
        private ImmutableMultimap<String, String> _tags = ImmutableMultimap.of();
        private Integer _limit;
        private Order _order;
        @NotNull
        private Map<String, Object> _otherArgs = Maps.newHashMap();
    }

    /**
     * Enum for representing possible kairosdb order values.
     */
    public enum Order {
        /**
         * Ascending sort order.
         */
        ASC,
        /**
         * Descending sort order.
         */
        DESC;

        /**
         * Encode the enumeration value as lower case.
         *
         * NOTE: KairosDb accepts either upper or lower case representation. This
         * model is otherwise a pass-through but converts all enumeration values
         * to lower case.
         *
         * @return json encoded value
         */
        @JsonValue
        public String toJson() {
            return name().toLowerCase(Locale.getDefault());
        }
    }
}
