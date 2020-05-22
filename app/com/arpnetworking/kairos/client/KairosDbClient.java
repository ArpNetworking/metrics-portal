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

import com.arpnetworking.kairos.client.models.MetricDataPoints;
import com.arpnetworking.kairos.client.models.MetricNamesResponse;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.TagNamesResponse;
import com.arpnetworking.kairos.client.models.TagsQuery;
import com.google.common.collect.ImmutableList;

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
    CompletionStage<MetricNamesResponse> queryMetricNames();

    /**
     * Queries KairosDB for a list of tags associated with a metric.
     *
     * @param query simplified metrics query to retrieve tags for
     * @return a query response only containing tags with values for each metric
     */
    CompletionStage<MetricsQueryResponse> queryMetricTags(TagsQuery query);

    /**
     * Query tag names in KairosDb.
     *
     * @return the response
     */
    CompletionStage<TagNamesResponse> listTagNames();

    /**
     * Persist metric data points.
     *
     * @param metricDataPoints the metric data points to persist
     * @return completion stage indicating success or failure of operation
     */
    CompletionStage<Void> addDataPoints(ImmutableList<MetricDataPoints> metricDataPoints);
}
