/**
 * Copyright 2018 Smartsheet.com
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

import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.google.common.collect.ImmutableList;

/**
 * Computes the "diff" of sequential data points.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class DiffAggregator extends TransformAggregator {

    @Override
    protected MetricsQueryResponse.QueryResult transformQueryResult(final MetricsQueryResponse.QueryResult result) {
        final ImmutableList.Builder<MetricsQueryResponse.DataPoint> transformed = ImmutableList.builder();
        Double last = null;
        for (MetricsQueryResponse.DataPoint dataPoint : result.getValues()) {
            final double currentValue = ((Number) dataPoint.getValue()).doubleValue();

            if (last != null) {
                transformed.add(
                        new MetricsQueryResponse.DataPoint.Builder()
                                .setTime(dataPoint.getTime())
                                .setValue(currentValue - last)
                                .build());
            }

            last = currentValue;
        }

        final MetricsQueryResponse.QueryResult.Builder newResult = MetricsQueryResponse.QueryResult.Builder.clone(result);
        newResult.setValues(transformed.build());
        return newResult.build();
    }

    /**
     * Protected constructor.
     *
     * @param builder Builder
     */
    private DiffAggregator(final Builder builder) {
        super(builder);
    }

    /**
     * Implementation of the builder pattern for a {@link DiffAggregator}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends BaseExecution.Builder<Builder, BaseExecution> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(DiffAggregator::new);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
