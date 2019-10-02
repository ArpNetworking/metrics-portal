/*
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
package com.arpnetworking.kairos.client;

import com.arpnetworking.kairos.client.models.KairosMetricNamesQueryResponse;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.RollupResponse;
import com.arpnetworking.kairos.client.models.RollupTask;
import com.arpnetworking.kairos.client.models.TagNamesResponse;
import com.arpnetworking.kairos.client.models.TagsQuery;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Client for accessing KairosDB APIs.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public interface KairosDbClient {
    /**
     * Executes a query for datapoints from  KairosDB.
     *
     * @param query the query
     * @return the response
     */
    CompletionStage<MetricsQueryResponse> queryMetrics(MetricsQuery query);

    /**
     * Queries KairosDB for metric names.
     *
     * @return the response
     */
    CompletionStage<KairosMetricNamesQueryResponse> queryMetricNames();

    /**
     * Queries KairosDB for a list of tags associated with a metric.
     *
     * @param query simplified metrics query to retrieve tags for
     * @return a query response only containing tags with values for each metric
     */
    CompletionStage<MetricsQueryResponse> queryMetricTags(TagsQuery query);

    /**
     * Queries KairosDB for list of rollups.
     *
     * @return the response
     */
    CompletionStage<List<RollupTask>> queryRollups();

    /**
     * Creates a rollup task in KairosDB.
     *
     * @param rollupTask the task to create
     * @return the response
     */
    CompletionStage<RollupResponse> createRollup(RollupTask rollupTask);

    /**
     * Updates an existing rollup task in KairosDB.
     * The id passed in the function arguments is used as the id of the RollupTask to update.  Any
     * id present in the RollupTask object will be ignored by KairosDB.
     *
     * @param id the id of the rollup task to update
     * @param rollupTask the task to update
     * @return the response
     */
    CompletionStage<RollupResponse> updateRollup(String id, RollupTask rollupTask);

    /**
     * Deletes an existing rollup task in KairosDB.
     *
     * @param id the id of task to delete
     * @return the response
     */
    CompletionStage<Void> deleteRollup(String id);

    /**
     * Query tag names in KairosDb.
     *
     * @return the response
     */
    CompletionStage<TagNamesResponse> listTagNames();
}
