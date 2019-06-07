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
import net.sf.oval.constraint.NotNull;

import javax.annotation.Nullable;

/**
 * Holds executor references and allows us to inject them into a controller for runtime selection.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
 */
public final class QueryExecutorRegistry {
    /**
     * Gets a query executor by name.
     * @param name name of the executor
     * @return the executor, or null if an executor of that name does not exist
     */
    @Nullable
    public QueryExecutor getExecutor(final String name) {
        return _executors.getOrDefault(name, null);
    }

    private QueryExecutorRegistry(final Builder builder) {
        _executors = builder._executors;
    }

    private final ImmutableMap<String, QueryExecutor> _executors;

    /**
     * Implementation of the Builder pattern for {@link QueryExecutorRegistry}.
     *
     * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
     */
    public static class Builder extends OvalBuilder<QueryExecutorRegistry> {
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
        public Builder setExecutors(final ImmutableMap<String, QueryExecutor> value) {
            _executors = value;
            return this;
        }

        @NotNull
        private ImmutableMap<String, QueryExecutor> _executors = ImmutableMap.of();
    }
}
