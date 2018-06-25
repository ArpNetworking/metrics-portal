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
package com.arpnetworking.metrics.portal.query.impl.mql;

import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MoreCollectors;
import models.internal.AlertTrigger;
import models.internal.MetricsQueryResult;
import models.internal.impl.DefaultAlertTrigger;
import models.internal.impl.DefaultMetricsQueryResult;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Period;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * An alert execution that will trigger when data has gone missing.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class DataAbsentAlertExecution extends BaseAlertExecution {
    @Override
    protected MetricsQueryResult.Result evaluateQueryResult(final MetricsQueryResult.Result result) {
        final ImmutableList.Builder<AlertTrigger> alerts = ImmutableList.builder();
        alerts.addAll(result.getAlerts());
        final DefaultMetricsQueryResult.Result.Builder newResult = DefaultMetricsQueryResult.Result.Builder.clone(result);

        final List<? extends MetricsQueryResult.DataPoint> values = result.getValues();

        if (values.size() > 0) {
            int x = 0;
            DefaultAlertTrigger.Builder alertBuilder = null;
            // We'll lazy create this
            final Supplier<ImmutableMap<String, String>> args = Suppliers.memoize(() -> createArgs(result));
            final Supplier<ImmutableMap<String, String>> groupBy = Suppliers.memoize(() -> createGroupBy(result));

            DateTime breachLast = null;

            while (x < values.size() - 1) {
                final MetricsQueryResult.DataPoint first = values.get(x);
                final MetricsQueryResult.DataPoint second = values.get(x + 1);
                // If we are in alert
                if (first.getTime().plus(getDwellPeriod()).isBefore(second.getTime())) {
                    // And we don't already have an alert builder going
                    if (alertBuilder == null) {
                        alertBuilder = new DefaultAlertTrigger.Builder()
                                .setTime(first.getTime().plus(getDwellPeriod()))
                                .setArgs(args.get())
                                .setGroupBy(groupBy.get())
                                .setMessage(getMessage(first));
                    }
                    breachLast = second.getTime();
                } else if (alertBuilder != null && breachLast.plus(getRecoveryPeriod()).isBefore(second.getTime())) {
                    // We are no longer in alert and the recovery period has expired
                    alertBuilder.setEndTime(Optional.of(breachLast));
                    alerts.add(alertBuilder.build());
                    alertBuilder = null;
                    breachLast = null;
                }

                x++;
            }

            final MetricsQueryResult.DataPoint last = values.get(values.size() - 1);
            final DateTime newDataCutoff = DateTime.now().minus(Duration.standardSeconds(120));
            if (last.getTime().plus(getDwellPeriod()).isBefore(newDataCutoff)) {
                alertBuilder = new DefaultAlertTrigger.Builder()
                        .setTime(last.getTime().plus(getDwellPeriod()))
                        .setArgs(args.get())
                        .setGroupBy(groupBy.get())
                        .setMessage(getMessage(last));
            }

            // If we still have an alertBuilder, the alert is ongoing, set the end time to the final sample
            if (alertBuilder != null) {
                alertBuilder.setEndTime(Optional.of(DateTime.now()));
                alerts.add(alertBuilder.build());
            }
        }

        newResult.setAlerts(alerts.build());
        return newResult.build();
    }

    @Override
    protected String getMessage(final MetricsQueryResult.DataPoint dataPoint) {
        final String missingDataTime = String.format("%d minutes", getDwellPeriod().toStandardMinutes().getMinutes());
        return String.format("Missing data for at least %s.", missingDataTime);
    }

    private ImmutableMap<String, String> createArgs(final MetricsQueryResult.Result result) {
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

    private ImmutableMap<String, String> createGroupBy(final MetricsQueryResult.Result result) {
        final ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        result.getGroupBy()
                .stream()
                .filter(MetricsQueryResult.QueryTagGroupBy.class::isInstance)
                .map(MetricsQueryResponse.QueryTagGroupBy.class::cast)
                .map(MetricsQueryResponse.QueryTagGroupBy::getGroup)
                .map(ImmutableMap::entrySet)
                .flatMap(Collection::stream)
                .forEach(entry -> builder.put(entry.getKey(), entry.getValue()));
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
