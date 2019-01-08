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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Represents a KairosDB Rollup task definition.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public final class RollupTask {
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String> getId() {
        return _id;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getName() {
        return _name;
    }

    @JsonProperty("execution_interval")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Sampling getExecutionInterval() {
        return _executionInterval;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ImmutableList<Rollup> getRollups() {
        return _rollups;
    }

    private RollupTask(final Builder builder) {
        _id = Optional.ofNullable(builder._id);
        _name = builder._name;
        _executionInterval = builder._executionInterval;
        _rollups = builder._rollups;
    }

    private final Optional<String> _id;
    private final String _name;
    private final Sampling _executionInterval;
    private final ImmutableList<Rollup> _rollups;

    /**
     * Implementation of the builder pattern for RollupTask.
     */
    public static final class Builder extends OvalBuilder<RollupTask> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(RollupTask::new);
        }

        /**
         * Sets the id. Optional. Cannot be empty.
         *
         * @param value the id
         * @return this {@link Builder}
         */
        public Builder setId(@Nullable final String value) {
            _id = value;
            return this;
        }

        /**
         * Sets the name of the rollup task. Required. Cannot be null or empty.
         *
         * @param value the name of the rollup task
         * @return this {@link Builder}
         */
        public Builder setName(final String value) {
            _name = value;
            return this;
        }

        /**
         * Sets the execution interval. Required. Cannot be null.
         *
         * @param value the group by clauses
         * @return this {@link Builder}
         */
        @JsonProperty("execution_interval")
        public Builder setExecutionInterval(final Sampling value) {
            _executionInterval = value;
            return this;
        }

        /**
         * Sets the rollups. Required. Cannot be null or empty.
         *
         * @param value the aggregators
         * @return this {@link Builder}
         */
        public Builder setRollups(final ImmutableList<Rollup> value) {
            _rollups = value;
            return this;
        }


        @NotEmpty
        private String _id;

        @NotNull
        @NotEmpty
        private String _name;

        @NotNull
        private Sampling _executionInterval;

        @NotNull
        @NotEmpty
        private ImmutableList<Rollup> _rollups = ImmutableList.of();
    }
}
