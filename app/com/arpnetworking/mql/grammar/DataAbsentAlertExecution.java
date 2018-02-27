/**
 * Copyright 2018 Smartsheet
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
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MoreCollectors;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Period;

import java.util.List;
import java.util.function.Supplier;

/**
 * An alert execution that will trigger when data has gone missing.
 */
public class DataAbsentAlertExecution extends BaseAlertExecution {
    @Override
    protected MetricsQueryResponse.QueryResult evaluateQueryResult(final MetricsQueryResponse.QueryResult result) {
        final ImmutableList.Builder<AlertTrigger> alerts = ImmutableList.builder();
        final MetricsQueryResponse.QueryResult.Builder newResult = MetricsQueryResponse.QueryResult.Builder.clone(result);

        final List<MetricsQueryResponse.DataPoint> values = result.getValues();

        if (values.size() > 0) {
            int x = 0;
            AlertTrigger.Builder alertBuilder = null;
            // We'll lazy create this
            final Supplier<ImmutableMap<String, String>> args = Suppliers.memoize(() -> createArgs(result));

            DateTime breachLast = null;

            while (x < values.size() - 1) {
                final MetricsQueryResponse.DataPoint first = values.get(x);
                final MetricsQueryResponse.DataPoint second = values.get(x + 1);
                // If we are in alert
                if (first.getTime().plus(getDwellPeriod()).isBefore(second.getTime())) {
                    // And we don't already have an alert builder going
                    if (alertBuilder == null) {
                        alertBuilder = new AlertTrigger.Builder()
                                .setTime(first.getTime().plus(getDwellPeriod()))
                                .setArgs(args.get());
                    }
                    breachLast = second.getTime();
                } else if (alertBuilder != null && breachLast.plus(getRecoveryPeriod()).isBefore(second.getTime())) {
                    // We are no longer in alert and the recovery period has expired
                    alertBuilder.setEndTime(breachLast);
                    alerts.add(alertBuilder.build());
                    alertBuilder = null;
                    breachLast = null;
                }

                x++;
            }

            final MetricsQueryResponse.DataPoint last = values.get(values.size() - 1);
            final DateTime newDataCutoff = DateTime.now().minus(Duration.standardSeconds(120));
            if (last.getTime().plus(getDwellPeriod()).isBefore(newDataCutoff)) {
                alertBuilder = new AlertTrigger.Builder()
                        .setTime(last.getTime().plus(getDwellPeriod()))
                        .setArgs(args.get());
            }

            // If we still have an alertBuilder, the alert is ongoing, set the end time to the final sample
            if (alertBuilder != null) {
                alertBuilder.setEndTime(DateTime.now());
                alerts.add(alertBuilder.build());
            }
        }

        newResult.setAlerts(alerts.build());
        return newResult.build();
    }

    private ImmutableMap<String, String> createArgs(final MetricsQueryResponse.QueryResult result) {
        final ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        builder.put("name", result.getName());
        result.getTags()
                .asMap()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().size() == 1)
                .forEach(entry -> builder.put(entry.getKey(), entry.getValue().stream().collect(MoreCollectors.onlyElement())));
        return builder.build();
    }

    /**
     * Protected constructor.
     *
     * @param builder the builder
     */
    protected DataAbsentAlertExecution(final Builder builder) {
        super(builder);
    }

    /**
     * Implementation of the Builder pattern for {@link DataAbsentAlertExecution}.
     */
    public static final class Builder extends BaseAlertExecution.Builder<Builder, DataAbsentAlertExecution> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(DataAbsentAlertExecution::new);
            setDwellPeriod(Period.minutes(1));
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
