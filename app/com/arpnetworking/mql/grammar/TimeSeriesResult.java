/**
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
package com.arpnetworking.mql.grammar;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.google.common.collect.ImmutableList;
import net.sf.oval.constraint.NotNull;

import java.util.List;

/**
 * Result of a TimeSeries query stage.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class TimeSeriesResult {
    public MetricsQueryResponse getResponse() {
        return _response;
    }

    public List<String> getWarnings() {
        return _warnings;
    }

    public List<String> getErrors() {
        return _errors;
    }

    private TimeSeriesResult(final Builder builder) {
        _response = builder._response;
        _warnings = builder._warnings;
        _errors = builder._errors;
    }

    private final MetricsQueryResponse _response;
    private final ImmutableList<String> _warnings;
    private final ImmutableList<String> _errors;

    /**
     * Implementation of the Builder pattern for {@link TimeSeriesResult}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends OvalBuilder<TimeSeriesResult> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(TimeSeriesResult::new);
        }

        /**
         * Sets the metrics query response. Required. Cannot be null.
         *
         * @param value the {@link MetricsQueryResponse}
         * @return this {@link Builder}
         */
        public Builder setResponse(final MetricsQueryResponse value) {
            _response = value;
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
        private MetricsQueryResponse _response;
        @NotNull
        private ImmutableList<String> _errors = ImmutableList.of();
        @NotNull
        private ImmutableList<String> _warnings = ImmutableList.of();
    }
}
