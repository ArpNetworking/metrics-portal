/*
 * Copyright 2019 Dropbox Inc.
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
package com.arpnetworking.kairos.service;


import com.arpnetworking.kairos.client.models.MetricNamesResponse;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.TagNamesResponse;
import com.arpnetworking.kairos.client.models.TagsQuery;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Defines a service provider that augments calls to a KairosDB backend server.
 *
 * This class applies logic to KairosDB requests to modify the behavior of KairosDB
 * in a way that provides specific behaviors that wouldn't be acceptable for a more
 * generic service like KairosDB.
 *
 * This closely mirrors the KairosDbClient interface but provides the ability to modify the
 * arguments as necessary for the additional functionality provided by an implementation of
 * this interface.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public interface KairosDbService {
    /**
     * Executes a query for datapoints from KairosDB.
     *
     * @param context the context associated with this query
     * @param query the metrics query
     * @return the response
     */
    CompletionStage<MetricsQueryResponse> queryMetrics(QueryContext context, MetricsQuery query);

    /**
     * Queries KairosDB for metric names.
     *
     * @param containing - metric names filter
     * @param prefix prefix that returned metric names must have (case-insensitive)
     * @param filterRollups - controls if rollup metrics are filtered from the response
     * @return the response
     */
    CompletionStage<MetricNamesResponse> queryMetricNames(Optional<String> containing, Optional<String> prefix, boolean filterRollups);

    /**
     * Queries KairosDB for a list of tags associated with a metric.
     *
     * @param query the tags query
     * @return a query response only containing tags with values for each metric
     */
    CompletionStage<MetricsQueryResponse> queryMetricTags(TagsQuery query);

    /**
     * Query tag names in KairosDb.
     *
     * @return the response
     */
    CompletionStage<TagNamesResponse> listTagNames();
}
