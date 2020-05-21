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

import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.arpnetworking.kairos.client.models.MetricDataPoints;
import com.arpnetworking.kairos.client.models.MetricNamesResponse;
import com.arpnetworking.kairos.client.models.MetricTags;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.TagNamesResponse;
import com.arpnetworking.kairos.client.models.TagsQuery;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletionStage;

/**
 * Client for accessing KairosDB APIs.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public abstract class KairosDbClient {
    /**
     * Executes a query for datapoints from  KairosDB.
     *
     * @param query the query
     * @return the response
     */
    public abstract CompletionStage<MetricsQueryResponse> queryMetrics(MetricsQuery query);

    /**
     * Queries KairosDB for metric names.
     *
     * @return the response
     */
    public abstract CompletionStage<MetricNamesResponse> queryMetricNames();

    /**
     * Queries KairosDB for a list of tags associated with a metric.
     *
     * @param query simplified metrics query to retrieve tags for
     * @return a query response only containing tags with values for each metric
     */
    public abstract CompletionStage<MetricsQueryResponse> queryMetricTags(TagsQuery query);

    /**
     * Query tag names in KairosDb.
     *
     * @return the response
     */
    public abstract CompletionStage<TagNamesResponse> listTagNames();

    /**
     * Persist metric data points.
     *
     * @param metricDataPoints the metric data points to persist
     * @return completion stage indicating success or failure of operation
     */
    public abstract CompletionStage<Void> addDataPoints(ImmutableList<MetricDataPoints> metricDataPoints);

    /**
     * Wrapper around {@link #queryMetricTags(TagsQuery)}, to get the set of tag-values for a single metric.
     *
     * @param metricName the name of the metric to get the tag names/values for.
     * @param time the time to get the tags at.
     * @return the set of tag names/values that the given metric has at <i>roughly</i> the given time.
     *   (Due to KairosDB implementation issues, it's actually the set of all names/values over a couple-week period
     *    that includes the given time.)
     */
    public CompletionStage<ImmutableMultimap<String, String>> queryMetricTags(final String metricName, final Instant time) {
        return queryMetricTags(ThreadLocalBuilder.build(TagsQuery.Builder.class, tqb -> tqb
                .setMetrics(ImmutableList.of(ThreadLocalBuilder.build(MetricTags.Builder.class, mtb -> mtb.setName(metricName))))
                .setStartTime(time)
                .setEndTime(time.plus(Duration.ofDays(1)))
        )).thenApply(response -> response.getQueries().get(0).getResults().get(0).getTags());
    }
}
