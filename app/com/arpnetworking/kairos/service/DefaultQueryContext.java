/*
 * Copyright 2020 Dropbox, Inc.
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

package com.arpnetworking.kairos.service;

import com.arpnetworking.commons.builder.OvalBuilder;
import net.sf.oval.constraint.NotNull;

/**
 * Default implementation for QueryContext.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class DefaultQueryContext implements QueryContext {
    private final QueryOrigin _origin;

    private DefaultQueryContext(final Builder builder) {
        _origin = builder._origin;
    }

    @Override
    public QueryOrigin getOrigin() {
        return _origin;
    }

    /**
     * Builder for instances of {@link DefaultQueryContext}.
     */
    public static final class Builder extends OvalBuilder<DefaultQueryContext> {
        @NotNull
        private QueryOrigin _origin;

        /**
         * Construct a new Builder instance.
         */
        public Builder() {
            super(DefaultQueryContext::new);
        }

        /**
         * Set the origin of this query context. Required. Cannot be null.
         *
         * @param value the origin
         * @return this Builder instance for chaining
         */
        public Builder setOrigin(final QueryOrigin value) {
            _origin = value;
            return this;
        }
    }
}
