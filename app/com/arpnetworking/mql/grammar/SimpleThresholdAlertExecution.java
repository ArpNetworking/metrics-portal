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

import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import models.internal.Operator;
import net.sf.oval.constraint.NotNull;

import java.util.function.Predicate;

/**
 * Applies a simple threshold to series.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class SimpleThresholdAlertExecution extends BaseAlertExecution {

    @Override
    protected boolean evaluateDataPoint(final MetricsQueryResponse.DataPoint dataPoint) {
        final double value = ((Number) dataPoint.getValue()).doubleValue();
        return _evaluator._eval.test(value);
    }

    private SimpleThresholdAlertExecution(final Builder builder) {
        super(builder);
        final Double threshold = builder._threshold;
        final Operator operator = builder._operator;
        switch (operator) {
            case EQUAL_TO:
                _evaluator = new TriggerEvaluator(threshold::equals);
                break;
            case GREATER_THAN:
                _evaluator = new TriggerEvaluator(v -> v > threshold);
                break;
            case GREATER_THAN_OR_EQUAL_TO:
                _evaluator = new TriggerEvaluator(v -> v >= threshold);
                break;
            case LESS_THAN:
                _evaluator = new TriggerEvaluator(v -> v < threshold);
                break;
            case LESS_THAN_OR_EQUAL_TO:
                _evaluator = new TriggerEvaluator(v -> v <= threshold);
                break;
            case NOT_EQUAL_TO:
                _evaluator = new TriggerEvaluator(v -> !threshold.equals(v));
                break;
            default:
                throw new IllegalArgumentException("Unknown operator on alert execution, operator=" + operator);
        }
    }

    private final TriggerEvaluator _evaluator;

    /**
     * Implementation of the Builder pattern for {@link SimpleThresholdAlertExecution}.
     */
    public static class Builder extends BaseAlertExecution.Builder<Builder, SimpleThresholdAlertExecution> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(SimpleThresholdAlertExecution::new);
        }

        /**
         * Sets the threshold. Required. Cannot be null.
         *
         * @param value the threshold
         * @return this {@link Builder}
         */
        public Builder setThreshold(final Double value) {
            _threshold = value;
            return this;
        }

        /**
         * Sets the operator. Required. Cannot be null.
         *
         * @param value the operator
         * @return this {@link Builder}
         */
        public Builder setOperator(final Operator value) {
            _operator = value;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @NotNull
        private Double _threshold = null;

        @NotNull
        private Operator _operator = null;
    }

    private static final class TriggerEvaluator {
        private TriggerEvaluator(final Predicate<Double> eval) {
            _eval = eval;
        }

        private final Predicate<Double> _eval;
    }
}
