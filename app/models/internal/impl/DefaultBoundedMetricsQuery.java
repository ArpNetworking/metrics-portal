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

package models.internal.impl;

import com.arpnetworking.commons.builder.OvalBuilder;
import edu.umd.cs.findbugs.annotations.Nullable;
import models.internal.BoundedMetricsQuery;
import models.internal.MetricsQueryFormat;
import net.sf.oval.constraint.NotNull;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Default implementation for {@code BoundedMetricsQuery}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class DefaultBoundedMetricsQuery implements BoundedMetricsQuery {
    private final String _query;
    private final MetricsQueryFormat _format;
    private final ZonedDateTime _startTime;
    private final Optional<ZonedDateTime> _endTime;

    private DefaultBoundedMetricsQuery(final Builder builder) {
        _query = builder._query;
        _format = builder._format;
        _startTime = builder._startTime;
        _endTime = Optional.ofNullable(builder._endTime);
    }

    @Override
    public ZonedDateTime getStartTime() {
        return _startTime;
    }

    @Override
    public Optional<ZonedDateTime> getEndTime() {
        return _endTime;
    }

    @Override
    public String getQuery() {
        return _query;
    }

    @Override
    public MetricsQueryFormat getQueryFormat() {
        return _format;
    }

    /**
     * Builder class for instances of {@link DefaultBoundedMetricsQuery}.
     */
    public static class Builder extends OvalBuilder<DefaultBoundedMetricsQuery> {
        @NotNull
        private String _query;
        @NotNull
        private MetricsQueryFormat _format;
        @NotNull
        private ZonedDateTime _startTime;
        @Nullable
        private ZonedDateTime _endTime;

        /**
         * Default constructor.
         */
        public Builder() {
            super(DefaultBoundedMetricsQuery::new);
        }

        /**
         * Sets the query. Required. Cannot be null.
         *
         * @param query the query.
         * @return This instance of {@code Builder} for chaining.
         */
        public Builder setQuery(final String query) {
            _query = query;
            return this;
        }

        /**
         * Sets the format. Required. Cannot be null.
         *
         * @param format the format.
         * @return This instance of {@code Builder} for chaining.
         */
        public Builder setFormat(final MetricsQueryFormat format) {
            _format = format;
            return this;
        }

        /**
         * Sets the start time. Required. Cannot be null.
         *
         * @param startTime the start time.
         * @return This instance of {@code Builder} for chaining.
         */
        public Builder setStartTime(final ZonedDateTime startTime) {
            _startTime = startTime;
            return this;
        }

        /**
         * Sets the end time. Optional.
         *
         * @param endTime the end time.
         * @return This instance of {@code Builder} for chaining.
         */
        public Builder setEndTime(final ZonedDateTime endTime) {
            _endTime = endTime;
            return this;
        }
    }
}
