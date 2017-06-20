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
import com.arpnetworking.metrics.util.ImmutableCollectors;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Base stage executor.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public abstract class BaseExecution implements StageExecution {
    /**
     * Called when all the dependencies have been resolved.
     *
     * @param results results of the dependencies
     * @return a new CompletionStage for the execution of this stage
     */
    public abstract CompletionStage<TimeSeriesResult> executeWithDependencies(Map<StageExecution, TimeSeriesResult> results);

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<TimeSeriesResult> execute() {
        final ConcurrentMap<StageExecution, TimeSeriesResult> results = Maps.newConcurrentMap();
        final CompletableFuture<?>[] dependencies = dependencies().stream()
                .map(dependency -> dependency.execute().thenApply(result -> results.put(dependency, result)))
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(dependencies)
                .thenCompose(v -> executeWithDependencies(results)
                .thenApply(result -> {
                        final TimeSeriesResult.Builder builder = TimeSeriesResult.Builder.clone(result);

                        builder.setErrors(
                                _unknownArgs.keySet()
                                        .stream()
                                        .map(key -> "Unknown argument " + key)
                                        .collect(ImmutableCollectors.toList()));
                        return builder.build();
                }));
    }

    @Override
    public List<StageExecution> dependencies() {
        return _dependencies;
    }

    /**
     * Protected constructor.
     *
     * @param builder Builder
     */
    protected BaseExecution(final Builder<?, ?> builder) {
        _dependencies = builder._dependencies;
        _unknownArgs = builder._other;
    }

    private final List<StageExecution> _dependencies;
    private final Map<String, Object> _unknownArgs;

    /**
     * Implementation of the Builder pattern for a {@link BaseExecution}.
     *
     * @param <B> type of the builder
     * @param <E> type of the Execution
     */
    public abstract static class Builder<B extends Builder<B, E>, E extends StageExecution> extends OvalBuilder<E> {
        /**
         * Add a dependency.
         *
         * @param value the dependency
         * @return this {@link Builder}
         */
        public B addDependency(final StageExecution value) {
            _dependencies.add(value);
            return self();
        }

        @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "Called from Jackson as the JsonAnySetter")
        @JsonAnySetter
        private void setOthers(final String key, final Object value) {
            _other.put(key, value);
        }

        /**
         * Protected constructor for subclasses.
         *
         * @param targetConstructor The constructor for the concrete type to be created by this builder.
         */
        protected Builder(final Function<B, E> targetConstructor) {
            super(targetConstructor);
        }

        /**
         * Gets the instance of the Builder with the proper type.
         *
         * @return this builder
         */
        protected abstract B self();

        private final List<StageExecution> _dependencies = Lists.newArrayList();
        private final Map<String, Object> _other = Maps.newHashMap();
    }
}
