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
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.google.common.collect.ImmutableList;
import models.internal.MetricsQueryResult;
import models.internal.TimeSeriesResult;
import net.sf.oval.constraint.NotNull;

/**
 * Result of a query stage.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class DefaultMetricsQueryResult implements MetricsQueryResult {
    @Override
    public TimeSeriesResult getQueryResult() {
        return _queryResult;
    }

    @Override
    public ImmutableList<String> getWarnings() {
        return _warnings;
    }

    @Override
    public ImmutableList<String> getErrors() {
        return _errors;
    }

    private DefaultMetricsQueryResult(final Builder builder) {
        _queryResult = builder._queryResult;
        _warnings = builder._warnings;
        _errors = builder._errors;
    }

    private final TimeSeriesResult _queryResult;
    private final ImmutableList<String> _warnings;
    private final ImmutableList<String> _errors;

    /**
     * Implementation of the Builder pattern for {@link DefaultMetricsQueryResult}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends OvalBuilder<MetricsQueryResult> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(DefaultMetricsQueryResult::new);
        }

        /**
         * Sets the metrics query response. Required. Cannot be null.
         *
         * @param value the {@link MetricsQueryResponse}
         * @return this {@link Builder}
         */
        public Builder setQueryResult(final TimeSeriesResult value) {
            _queryResult = value;
            return this;
        }

        /**
         * Sets the errors. Optional. Cannot be null.
         *
         * @param value the list of errors
         * @return this {@link Builder}
         */
        public Builder setErrors(final ImmutableList<String> value) {
            _errors = value;
            return this;
        }

        /**
         * Sets the warnings. Optional. Cannot be null.
         *
         * @param value the list of warnings
         * @return this {@link Builder}
         */
        public Builder setWarnings(final ImmutableList<String> value) {
            _warnings = value;
            return this;
        }

        @NotNull
        private TimeSeriesResult _queryResult;
        @NotNull
        private ImmutableList<String> _errors = ImmutableList.of();
        @NotNull
        private ImmutableList<String> _warnings = ImmutableList.of();
    }
}
