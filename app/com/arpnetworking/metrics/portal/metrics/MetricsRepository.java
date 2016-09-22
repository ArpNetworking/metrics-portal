/**
 * Copyright 2016 Smartsheet.com
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
package com.arpnetworking.metrics.portal.metrics;

import models.internal.Metric;
import models.internal.MetricsQuery;
import models.internal.Organization;
import models.internal.QueryResult;

/**
 * Repository for querying metrics.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public interface MetricsRepository {
    /**
     * Open the <code>ExpressionRepository</code>.
     */
    void open();

    /**
     * Close the <code>ExpressionRepository</code>.
     */
    void close();

    /**
     * Create a query against the expressions repository.
     *
     * @param organization Organization to search in.
     * @return Instance of <code>ExpressionQuery</code>.
     */
    MetricsQuery createQuery(Organization organization);

    /**
     * Query expressions.
     *
     * @param query Instance of <code>ExpressionQuery</code>.
     * @return The <code>Collection</code> of all expressions.
     */
    QueryResult<Metric> query(MetricsQuery query);

    /**
     * Retrieve the total number of expressions in the repository.
     *
     * @param organization The organization owning the expressions.
     * @return The total number of expressions.
     */
    long getExpressionCount(Organization organization);
}
