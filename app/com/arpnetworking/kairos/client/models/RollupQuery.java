/*
 * Copyright 2019 Dropbox Inc.
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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

/**
 * Defines the start time and metrics used for a Rollup.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public final class RollupQuery {

    @JsonProperty("start_relative")
    public Sampling getStartRelative() {
        return _startRelative;
    }

    public ImmutableList<Metric> getMetrics() {
        return _metrics;
    }

    private RollupQuery(final Builder builder) {
        _startRelative = builder._startRelative;
        _metrics = builder._metrics;
    }

    private final Sampling _startRelative;
    private final ImmutableList<Metric> _metrics;

    /**
     * Implementation of the builder pattern for RollupQuery.
     */
    public static final class Builder extends OvalBuilder<RollupQuery> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(RollupQuery::new);
        }

        /**
         * Sets the relative start time of the query. Required. Cannot be null.
         *
         * @param value the relative start time
         * @return this {@link Builder}
         */
        @JsonProperty("start_relative")
        public Builder setStartRelative(final Sampling value) {
            _startRelative = value;
            return this;
        }

        /**
         * Sets the metrics of the query. Required. Cannot be null or empty.
         *
         * @param value the metrics list
         * @return this {@link Builder}
         */
        public Builder setMetrics(final ImmutableList<Metric> value) {
            _metrics = value;
            return this;
        }

        @NotNull
        private Sampling _startRelative;

        @NotNull
        @NotEmpty
        private ImmutableList<Metric> _metrics;
    }
}
