/*
 * Copyright 2020 Dropbox Inc.
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
package controllers;

import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.Aggregator;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricNamesResponse;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.Sampling;
import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.arpnetworking.kairos.config.MetricsQueryConfig;
import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.Timer;
import com.arpnetworking.testing.SerializationTestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import play.libs.ws.WSClient;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;


import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

/**
 * Test class for the KairosDbProxyController.
 * @author Gil Markham (gmarkham at dropbox dot com)
 */
public class KairosDbProxyControllerTest {
    private static final ObjectMapper OBJECT_MAPPER = SerializationTestUtils.getApiObjectMapper();

    @Mock
    private Config _mockConfig;
    @Mock
    private WSClient _mockWSClient;
    @Mock
    private KairosDbClient _mockKairosDbClient;
    @Mock
    private MetricsFactory _mockMetricsFactory;
    @Mock
    private Metrics _mockMetrics;
    @Mock
    private Timer _mockTimer;
    @Mock
    private MetricsQueryConfig _mockMetricsqueryConfig;

    private KairosDbProxyController _controller;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(_mockConfig.getString(eq("kairosdb.uri"))).thenReturn("http://example.com/");
        when(_mockConfig.getBoolean(eq("kairosdb.proxy.requireAggregators"))).thenReturn(true);
        when(_mockConfig.getBoolean(eq("kairosdb.proxy.addMergeAggregator"))).thenReturn(true);
        when(_mockMetricsFactory.create()).thenReturn(_mockMetrics);
        when(_mockMetrics.createTimer(any())).thenReturn(_mockTimer);
        when(_mockKairosDbClient.queryMetricNames()).thenReturn(CompletableFuture.completedFuture(
                new MetricNamesResponse.Builder()
                        .setResults(ImmutableList.of("metric1", "metric2"))
                        .build()
        ));

        _controller = new KairosDbProxyController(
                _mockConfig,
                _mockWSClient,
                _mockKairosDbClient,
                OBJECT_MAPPER,
                _mockMetricsFactory,
                _mockMetricsqueryConfig
        );
    }

    @Test
    public void testQueryRequiresAggregator() {
        final Metric.Builder metric1Builder = new Metric.Builder()
                .setName("metric1")
                .setAggregators(ImmutableList.of(new Aggregator.Builder().setName("count").build()));
        final Metric.Builder metric2Builder = new Metric.Builder()
                .setName("metric2");
        final MetricsQuery.Builder builder = new MetricsQuery.Builder()
                .setStartTime(Instant.now())
                .setMetrics(ImmutableList.of(metric1Builder.build(), metric2Builder.build()));

        Http.RequestBuilder request = Helpers.fakeRequest()
                .method(Helpers.POST)
                .uri("/api/v1/datapoints/query")
                .header("Content-Type", "application/json")
                .bodyJson(OBJECT_MAPPER.<JsonNode>valueToTree(builder.build()));

        // ***
        // Test failure case where one metric doesn't have an aggregator
        // ***
        Result result = Helpers.invokeWithContext(request, Helpers.contextComponents(), () -> {
            final CompletionStage<Result> completionStage = _controller.queryMetrics();
            return completionStage.toCompletableFuture().get(10, TimeUnit.SECONDS);
        });

        assertEquals(Http.Status.BAD_REQUEST, result.status());
        assertEquals("All queried metrics must have at least one aggregator",
                Helpers.contentAsString(result));

        // ***
        // Test success case where both metrics have aggregators
        // ***
        metric2Builder.setAggregators(ImmutableList.of(new Aggregator.Builder().setName("sum").build()));
        builder.setMetrics(ImmutableList.of(metric1Builder.build(), metric2Builder.build()));

        request = Helpers.fakeRequest()
                .method(Helpers.POST)
                .uri("/api/v1/datapoints/query")
                .header("Content-Type", "application/json")
                .bodyJson(OBJECT_MAPPER.<JsonNode>valueToTree(builder.build()));

        when(_mockKairosDbClient.queryMetrics(any())).thenReturn(
                CompletableFuture.completedFuture(new MetricsQueryResponse.Builder()
                        .setQueries(ImmutableList.of()).build())
        );
        result = Helpers.invokeWithContext(request, Helpers.contextComponents(), () -> {
            final CompletionStage<Result> completionStage = _controller.queryMetrics();
            return completionStage.toCompletableFuture().get(10, TimeUnit.SECONDS);
        });

        assertEquals(Http.Status.OK, result.status());
        assertEquals("{\"queries\":[]}", Helpers.contentAsString(result));
    }

    @Test
    public void testQueryRequiresAggregatorOff() {
        when(_mockConfig.getBoolean(eq("kairosdb.proxy.requireAggregators"))).thenReturn(false);

        final KairosDbProxyController controller = new KairosDbProxyController(
                _mockConfig,
                _mockWSClient,
                _mockKairosDbClient,
                OBJECT_MAPPER,
                _mockMetricsFactory,
                _mockMetricsqueryConfig
        );

        final Metric.Builder metric1Builder = new Metric.Builder()
                .setName("metric1")
                .setAggregators(ImmutableList.of(new Aggregator.Builder().setName("count").build()));
        final Metric.Builder metric2Builder = new Metric.Builder()
                .setName("metric2");
        final MetricsQuery.Builder builder = new MetricsQuery.Builder()
                .setStartTime(Instant.now())
                .setMetrics(ImmutableList.of(metric1Builder.build(), metric2Builder.build()));

        mockQueryMetrics(controller, builder.build(), any());
    }

    @Test
    public void testAddMergeAggregatorOn() {
        final Metric.Builder metric1Builder = new Metric.Builder()
                .setName("metric1")
                .setAggregators(ImmutableList.of(new Aggregator.Builder().setName("count").build()));
        final Metric.Builder metric2Builder = new Metric.Builder()
                .setName("metric2")
                .setAggregators(ImmutableList.of(new Aggregator.Builder().setName("filter").build()));
        final MetricsQuery.Builder builder = new MetricsQuery.Builder()
                .setStartTime(Instant.now())
                .setMetrics(ImmutableList.of(metric1Builder.build(), metric2Builder.build()));

        mockQueryMetrics(_controller, builder.build(), builder.setMetrics(ImmutableList.of(metric1Builder.build(),
                        metric2Builder.setAggregators(ImmutableList.of(
                                new Aggregator.Builder().setName("merge").build(),
                                new Aggregator.Builder().setName("filter").build())).build())).build());
    }

    @Test
    public void testAddMergeAggregatorOff() {
        when(_mockConfig.getBoolean(eq("kairosdb.proxy.addMergeAggregator"))).thenReturn(false);

        final KairosDbProxyController controller = new KairosDbProxyController(
                _mockConfig,
                _mockWSClient,
                _mockKairosDbClient,
                OBJECT_MAPPER,
                _mockMetricsFactory,
                _mockMetricsqueryConfig
        );

        final Metric metric1 = new Metric.Builder()
                .setName("metric1")
                .setAggregators(ImmutableList.of(new Aggregator.Builder().setName("count").build()))
                .build();
        final Metric metric2 = new Metric.Builder()
                .setName("metric2")
                .setAggregators(ImmutableList.of(new Aggregator.Builder().setName("filter").build()))
                .build();
        final MetricsQuery metricsQuery = new MetricsQuery.Builder()
                .setStartTime(Instant.now())
                .setMetrics(ImmutableList.of(metric1, metric2))
                .build();

        mockQueryMetrics(controller, metricsQuery, metricsQuery);
    }

    private void mockQueryMetrics(final KairosDbProxyController controller, final MetricsQuery metricsQuery,
                                  final MetricsQuery newMetricsQuery) {
        final Http.RequestBuilder request = play.test.Helpers.fakeRequest()
                .method(play.test.Helpers.POST)
                .uri("/api/v1/datapoints/query")
                .header("Content-Type", "application/json")
                .bodyJson(OBJECT_MAPPER.<JsonNode>valueToTree(metricsQuery));

        when(_mockKairosDbClient.queryMetrics(newMetricsQuery)).thenReturn(
                CompletableFuture.completedFuture(new MetricsQueryResponse.Builder()
                        .setQueries(ImmutableList.of()).build())
        );

        final Result result = Helpers.invokeWithContext(request, Helpers.contextComponents(), () -> {
            final CompletionStage<play.mvc.Result> completionStage = controller.queryMetrics();
            return completionStage.toCompletableFuture().get(10, TimeUnit.SECONDS);
        });

        assertEquals(Http.Status.OK, result.status());
        assertEquals("{\"queries\":[]}", Helpers.contentAsString(result));
    }

    @Test
    public void testCheckAndAddMergeAggregator() {
        final Metric metric1 = new Metric.Builder()
                .setName("metric1")
                .setAggregators(ImmutableList.of(
                        new Aggregator.Builder().setName("filter").build(),
                        new Aggregator.Builder().setName("max").setSampling(
                                new Sampling.Builder().setValue(1).setUnit(SamplingUnit.HOURS).build()).build()))
                .build();
        final Metric metric2 = new Metric.Builder()
                .setName("metric2")
                .setAggregators(ImmutableList.of(new Aggregator.Builder().setName("min").build()))
                .build();
        final MetricsQuery metricsQuery = new MetricsQuery.Builder()
                .setStartTime(Instant.now())
                .setMetrics(ImmutableList.of(metric1, metric2))
                .build();

        final MetricsQuery newMetricsQuery = _controller.checkAndAddMergeAggregator(metricsQuery);
        assertEquals("merge", newMetricsQuery.getMetrics().get(0).getAggregators().get(0).getName());
        assertEquals("filter", newMetricsQuery.getMetrics().get(0).getAggregators().get(1).getName());
        assertEquals("max", newMetricsQuery.getMetrics().get(0).getAggregators().get(2).getName());
        assertEquals(newMetricsQuery.getMetrics().get(0).getAggregators().get(2).getSampling(),
                newMetricsQuery.getMetrics().get(0).getAggregators().get(0).getSampling());
        assertEquals("min", newMetricsQuery.getMetrics().get(1).getAggregators().get(0).getName());
    }
}
