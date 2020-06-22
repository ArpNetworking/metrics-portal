/*
 * Copyright 2019 Inscope Metrics, Inc
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
package com.arpnetworking.metrics.portal.query;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.google.common.collect.ImmutableMap;
import models.internal.MetricsQueryFormat;
import net.sf.oval.constraint.NotNull;

import java.util.Optional;

/**
 * Holds executor references to allow for runtime selection by query format.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
 */
public final class QueryExecutorRegistry {

    private final ImmutableMap<MetricsQueryFormat, QueryExecutor> _executors;

    /**
     * Gets a query executor by format.
     *
     * @param format The query format
     * @return the executor, or {@link Optional#empty} if an executor of that name does not exist
     */
    public Optional<QueryExecutor> getExecutor(final MetricsQueryFormat format) {
        return Optional.ofNullable(_executors.getOrDefault(format, null));
    }

    private QueryExecutorRegistry(final Builder builder) {
        _executors = builder._executors;
    }

    /**
     * Implementation of the Builder pattern for {@link QueryExecutorRegistry}.
     *
     * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
     */
    public static class Builder extends OvalBuilder<QueryExecutorRegistry> {
        @NotNull
        private ImmutableMap<MetricsQueryFormat, QueryExecutor> _executors = ImmutableMap.of();

        /**
         * Public constructor.
         */
        public Builder() {
            super(QueryExecutorRegistry::new);
        }

        /**
         * Sets the executors map. Optional. Cannot be null. Defaults to empty.
         *
         * @param value the time
         * @return this {@link Builder}
         */
        public Builder setExecutors(final ImmutableMap<MetricsQueryFormat, QueryExecutor> value) {
            _executors = value;
            return this;
        }
    }
}
