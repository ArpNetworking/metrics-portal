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
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

/**
 * Defines a query and destination for a RollupTask.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public final class Rollup {
    @JsonProperty("save_as")
    public String getSaveAs() {
        return _saveAs;
    }

    public RollupQuery getQuery() {
        return _query;
    }

    private Rollup(final Builder builder) {
        _saveAs = builder._saveAs;
        _query = builder._query;
    }

    private final String _saveAs;
    private final RollupQuery _query;

    /**
     * Implementation of the builder pattern for Rollup.
     */
    public static final class Builder extends OvalBuilder<Rollup> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(Rollup::new);
        }

        /**
         * Sets the name the of the metrics the results of the query will be saved as. Required. Cannot be null.
         *
         * @param value the results metric name
         * @return this {@link Builder}
         */
        @JsonProperty("save_as")
        public Builder setSaveAs(final String value) {
            _saveAs = value;
            return this;
        }

        /**
         * Sets the rollup query for this task. Required. Cannot be null.
         *
         * @param value the rollup query
         * @return this {@link Builder}
         */
        public Builder setQuery(final RollupQuery value) {
            _query = value;
            return this;
        }

        @NotNull
        @NotEmpty
        private String _saveAs;

        @NotNull
        private RollupQuery _query;
    }
}
