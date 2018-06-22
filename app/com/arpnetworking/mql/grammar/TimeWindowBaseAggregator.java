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

import net.sf.oval.constraint.NotNull;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Base aggregator that assists higher level aggregators by bucketing the data to aggregate.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class TimeWindowBaseAggregator extends BaseExecution {
    @Override
    public CompletionStage<TimeSeriesResult> executeWithDependencies(final Map<StageExecution, TimeSeriesResult> results) {
        return null;
    }

    /**
     * Protected constructor.
     *
     * @param builder the builder
     */
    protected TimeWindowBaseAggregator(final Builder<?, ?> builder) {
        super(builder);
        _bucketWidth = builder._bucketWidth;
    }

    private final Duration _bucketWidth;


    /**
     * Implementation of the Builder pattern for {@link TimeWindowBaseAggregator}.
     *
     * @param <B> type of the builder
     * @param <E> type of the execution
     */
    public abstract static class Builder<B extends BaseExecution.Builder<B, E>, E extends TimeWindowBaseAggregator>
            extends BaseExecution.Builder<B, E> {
        /**
         * Set the bucket width. Cannot be null. Defaults to 1 minute.
         *
         * @param value the bucket width
         * @return this {@link Builder}
         */
        public B setBucketWidth(final Duration value) {
            _bucketWidth = value;
            return self();
        }
        /**
         * Protected constructor for subclasses.
         *
         * @param targetConstructor The constructor for the concrete type to be created by this builder.
         */
        protected Builder(final Function<B, E> targetConstructor) {
            super(targetConstructor);
        }

        @NotNull
        private Duration _bucketWidth = Duration.ofMinutes(1);
    }
}
