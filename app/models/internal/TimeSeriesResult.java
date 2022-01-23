/*
 * Copyright 2018 Inscope Metrics, Inc
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
package models.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;

import java.time.Instant;

/**
 * Represents the successful result of executing a metrics query.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
 */
public interface TimeSeriesResult {
    /**
     * Gets the {@link Query} objects that form the result.
     *
     * @return the queries
     */
    ImmutableList<? extends Query> getQueries();

    /**
     * Represents the query in a {@link TimeSeriesResult}.
     *
     * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
     */
    interface Query {
        /**
         * Gets the number of samples.
         *
         * @return the number of samples
         */
        long getSampleSize();

        /**
         * Gets the results of the query.
         *
         * @return a list of {@link Result}
         */
        ImmutableList<? extends Result> getResults();
    }

    /**
     * Represents the result of a query in a {@link Query}.
     *
     * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
     */
    interface Result {
        /**
         * Gets the name of the metric.
         *
         * @return the metric name
         */
        String getName();

        /**
         * Gets the data points.
         *
         * @return a list of {@link DataPoint}
         */
        ImmutableList<? extends DataPoint> getValues();

        /**
         * Gets the tags.
         *
         * @return the tags
         */
        ImmutableMultimap<String, String> getTags();

        /**
         * Gets the alert triggers (if any).
         *
         * @return list of triggers
         */
        ImmutableList<? extends AlertTrigger> getAlerts();

        /**
         * Gets group-by parameters.
         *
         * @return group by details
         */
        ImmutableList<? extends QueryGroupBy> getGroupBy();
    }

    /**
     * Represents the group by arguments in a query's {@link Result}.
     *
     * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
     */
    interface QueryGroupBy {
    }

    /**
     * Represents the group by arguments in a query's {@link Result}.
     * Specifically, group by values that are defined by tags.
     *
     * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
     */
    interface QueryTagGroupBy extends QueryGroupBy {
        /**
         * Gets the tags.
         *
         * @return the tags
         */
        ImmutableList<String> getTags();

        /**
         * The key/value pairs for this group by record.
         *
         * @return key/value map for the group by tags
         */
        ImmutableMap<String, String> getGroup();
    }

    /**
     * Represents the group by arguments in a query's {@link Result}.
     * Specifically, group by types of the values.
     *
     * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
     */
    interface QueryTypeGroupBy extends QueryGroupBy {
        /**
         * Gets the group by type.
         *
         * @return the group by type
         */
        String getType();
    }

    /**
     * Represents a data point in a {@link Result}.
     *
     * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
     */
    interface DataPoint {
        /**
         * Gets the time of the datapoint.
         *
         * @return the datapoint time
         */
        Instant getTime();

        /**
         * Gets the recorded value of the datapoint.
         *
         * @return the datapoint value
         */
        Object getValue();
    }
}
