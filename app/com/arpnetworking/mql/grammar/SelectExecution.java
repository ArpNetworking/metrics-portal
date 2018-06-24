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

import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.MetricsQuery;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Represents an execution of a metrics SELECT query.  Holds incoming references and binding name.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class SelectExecution extends BaseExecution {
    @Override
    public CompletionStage<TimeSeriesResult> executeWithDependencies(final Map<StageExecution, TimeSeriesResult> results) {
        return _client.queryMetrics(_query).thenApply(
                result -> new TimeSeriesResult.Builder().setResponse(result).build());
    }

    private SelectExecution(final Builder builder) {
        super(builder);
        _query = builder._query;
        _client = builder._client;
    }

    public MetricsQuery getQuery() {
        return _query;
    }

    public KairosDbClient getClient() {
        return _client;
    }

    private final MetricsQuery _query;
    private final KairosDbClient _client;

    /**
     * Implementation of the Builder pattern for a {@link SelectExecution}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends BaseExecution.Builder<Builder, SelectExecution> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(SelectExecution::new);
        }

        /**
         * Sets the KairosDB client.
         *
         * @param value the client
         * @return this {@link Builder}
         */
        public Builder setClient(final KairosDbClient value) {
            _client = value;
            return this;
        }

        /**
         * Sets the query builder.
         *
         * @param value the builder.
         * @return this {@link Builder}
         */
        public Builder setQuery(final MetricsQuery value) {
            _query = value;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        private MetricsQuery _query;
        private KairosDbClient _client;
    }
}
