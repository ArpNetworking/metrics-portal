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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

/**
 * Holds the data for a Metric element of the query.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class Metric {
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
    public ImmutableList<MetricsQuery.GroupBy> getGroupBy() {
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
    private final ImmutableList<MetricsQuery.GroupBy> _groupBy;

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

        @NotNull
        @NotEmpty
        private String _name;

        @NotNull
        private ImmutableList<Aggregator> _aggregators = ImmutableList.of();

        @NotNull
        private ImmutableList<MetricsQuery.GroupBy> _groupBy = ImmutableList.of();

        @NotNull
        private ImmutableMultimap<String, String> _tags = ImmutableMultimap.of();
    }
}
