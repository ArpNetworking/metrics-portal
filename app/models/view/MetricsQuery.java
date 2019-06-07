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
package models.view;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.logback.annotations.Loggable;
import models.internal.impl.DefaultMetricsQuery;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.time.ZonedDateTime;

/**
 * View model for metrics queries.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
@Loggable
public final class MetricsQuery {
    public String getQuery() {
        return _query;
    }

    public ZonedDateTime getStart() {
        return _start;
    }

    public ZonedDateTime getEnd() {
        return _end;
    }

    public String getExecutor() {
        return _executor;
    }

    /**
     * Converts this view model into an internal model.
     *
     * @return a new internal model derived from this view model
     */
    public models.internal.MetricsQuery toInternal() {
        return new DefaultMetricsQuery.Builder()
                .setEnd(_end)
                .setQuery(_query)
                .setStart(_start)
                .build();
    }

    private MetricsQuery(final Builder builder) {
        _query = builder._query;
        _start = builder._start;
        _end = builder._end;
        _executor = builder._executor;
    }

    private final String _query;
    private final ZonedDateTime _start;
    private final ZonedDateTime _end;
    private final String _executor;

    /**
     * Implementation of the builder pattern for {@link MetricsQuery}.
     */
    public static final class Builder extends OvalBuilder<MetricsQuery> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(MetricsQuery::new);
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
         * Sets the end time. Required. Cannot be null.
         *
         * @param value the end time
         * @return this {@link Builder}
         */
        public Builder setEnd(final ZonedDateTime value) {
            _end = value;
            return this;
        }

        /**
         * Sets the start time. Required. Cannot be null.
         *
         * @param value the start time
         * @return this {@link Builder}
         */
        public Builder setStart(final ZonedDateTime value) {
            _start = value;
            return this;
        }

        /**
         * Sets the executor. Optional. Cannot be null or empty. Defaults to "default".
         *
         * @param value the executor to use to run the query
         * @return this {@link Builder}
         */
        public Builder setExecutor(final String value) {
            _executor = value;
            return this;
        }

        @NotNull
        @NotEmpty
        private String _query;
        @NotNull
        private ZonedDateTime _start;
        @NotNull
        private ZonedDateTime _end;
        @NotEmpty
        @NotNull
        private String _executor = "default";
    }
}
