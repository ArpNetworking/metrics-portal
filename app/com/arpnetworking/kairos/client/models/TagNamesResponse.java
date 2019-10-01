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

import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.sf.oval.constraint.NotNull;

/**
 * Defines the response to a tag query.
 *
 * https://kairosdb.github.io/docs/build/html/restapi/ListTagNames.html
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class TagNamesResponse {

    @JsonAnyGetter
    public ImmutableMap<String, Object> getOtherArgs() {
        return _otherArgs;
    }

    public ImmutableSet<String> getResults() {
        return _results;
    }

    private TagNamesResponse(final Builder builder) {
        _otherArgs = builder._otherArgs;
        _results = builder._results;
    }

    private final ImmutableMap<String, Object> _otherArgs;
    private final ImmutableSet<String> _results;

    /**
     * Implementation of the builder pattern for {@link TagNamesResponse}.
     */
    public static final class Builder extends ThreadLocalBuilder<TagNamesResponse> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(TagNamesResponse::new);
        }

        /**
         * Adds an "unknown" parameter. Optional.
         *
         * @param key key for the entry
         * @param value value for the entry
         * @return this {@link MetricsQueryResponse.Builder}
         */
        @JsonAnySetter
        public Builder addOtherArg(final String key, final Object value) {
            _otherArgs = new ImmutableMap.Builder<String, Object>().putAll(_otherArgs).put(key, value).build();
            return this;
        }

        /**
         * Sets the tag names result of the response. Required. Cannot be null.
         *
         * @param value the resulting tag name list
         * @return this {@link Builder}
         */
        public Builder setResults(final ImmutableSet<String> value) {
            _results = value;
            return this;
        }

        @Override
        protected void reset() {
            _otherArgs = ImmutableMap.of();
            _results = null;
        }

        @NotNull
        private ImmutableMap<String, Object> _otherArgs = ImmutableMap.of();
        @NotNull
        private ImmutableSet<String> _results;
    }
}
