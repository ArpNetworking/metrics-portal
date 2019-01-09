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
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

/**
 * Defines the response to the creation or update of a RollupTask.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public final class RollupResponse {
    public String getId() {
        return _id;
    }

    public String getName() {
        return _name;
    }

    private RollupResponse(final Builder builder) {
        _id = builder._id;
        _name = builder._name;
    }

    private final String _id;
    private final String _name;

    /**
     * Implementation of the builder pattern for Rollup.
     */
    public static final class Builder extends OvalBuilder<RollupResponse> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(RollupResponse::new);
        }

        /**
         * Sets the id of the rollup task created/updated. Required. Cannot be null or empty.
         *
         * @param value the id
         * @return this {@link Builder}
         */
        public Builder setId(final String value) {
            _id = value;
            return this;
        }

        /**
         * Sets the name of the rollup task. Required. Cannot be null or empty.
         *
         * @param value the rollup task name
         * @return this {@link Builder}
         */
        public Builder setName(final String value) {
            _name = value;
            return this;
        }

        @NotNull
        @NotEmpty
        private String _id;

        @NotNull
        @NotEmpty
        private String _name;
    }
}
