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
public class QueryExecutorRegistry {
    private QueryExecutorRegistry(final Builder builder) {
        _executors = builder._executors;
    }

    /**
     * Gets a query executor by name.
     * @param name name of the executor
     * @return the executor, or null if an executor of that name does not exist
     */
    @Nullable
    public QueryExecutor getExecutor(final String name) {
        return _executors.getOrDefault(name, null);
    }

    private final ImmutableMap<String, QueryExecutor> _executors;

    /**
     * Implementation of the Builder pattern for {@link QueryExecutorRegistry}.
     *
     * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
     */
    public static class Builder extends OvalBuilder<QueryExecutorRegistry> {
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
        ImmutableMap<String, QueryExecutor> _executors = ImmutableMap.of();
    }
}
