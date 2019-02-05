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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Holds the data for a Metric element of the query.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
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
    public ImmutableList<MetricsQuery.GroupBy> getGroupBy() {
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

    private Metric(final Builder builder) {
        _name = builder._name;
        _tags = builder._tags;
        _aggregators = builder._aggregators;
        _groupBy = builder._groupBy;
        _limit = Optional.ofNullable(builder._limit);
        _order = Optional.ofNullable(builder._order);
    }

    private final String _name;
    private final ImmutableMultimap<String, String> _tags;
    private final ImmutableList<Aggregator> _aggregators;
    private final ImmutableList<MetricsQuery.GroupBy> _groupBy;
    private final Optional<Integer> _limit;
    private final Optional<Order> _order;

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
        @JsonProperty("group_by")
        public Builder setGroupBy(final ImmutableList<MetricsQuery.GroupBy> value) {
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

        @NotNull
        @NotEmpty
        private String _name;

        @NotNull
        private ImmutableList<Aggregator> _aggregators = ImmutableList.of();

        @NotNull
        private ImmutableList<MetricsQuery.GroupBy> _groupBy = ImmutableList.of();

        @NotNull
        private ImmutableMultimap<String, String> _tags = ImmutableMultimap.of();

        private Integer _limit;

        private Order _order;
    }

    /**
     * Enum for representing possible kairosdb order values.
     */
    public enum Order {
        /**
         * Ascending sort order.
         */
        ASC("asc"),
        /**
         * Descending sort order.
         */
        DESC("desc");

        Order(final String strValue) {
            _strValue = strValue;
        }

        @JsonValue
        @Override
        public String toString() {
            return _strValue;
        }

        private final String _strValue;
    }
}
