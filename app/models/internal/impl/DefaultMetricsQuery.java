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
package models.internal.impl;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.logback.annotations.Loggable;
import models.internal.MetricsQueryFormat;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

/**
 * Internal model for metrics queries.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
@Loggable
public final class DefaultMetricsQuery implements models.internal.MetricsQuery {
    @Override
    public String getQuery() {
        return _query;
    }

    @Override
    public MetricsQueryFormat getQueryFormat() {
        return _format;
    }

    private DefaultMetricsQuery(final Builder builder) {
        _query = builder._query;
        _format = builder._format;
    }

    private final String _query;
    private final MetricsQueryFormat _format;

    /**
     * Implementation of the builder pattern for {@link DefaultMetricsQuery}.
     */
    public static final class Builder extends OvalBuilder<DefaultMetricsQuery> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(DefaultMetricsQuery::new);
        }

        /**
         * Sets the query. Required. Cannot be null or empty.
         *
         * @param value the query
         * @return this {@link Builder}
         */
        public Builder setQuery(final String value) {
            _query = value;
            return this;
        }

        /**
         * Sets the format. Required. Cannot be null.
         *
         * @param value the start time
         * @return this {@link Builder}
         */
        public Builder setFormat(final MetricsQueryFormat value) {
            _format = value;
            return this;
        }
        @NotNull
        @NotEmpty
        private String _query;
        @NotNull
        private MetricsQueryFormat _format;
    }
}
