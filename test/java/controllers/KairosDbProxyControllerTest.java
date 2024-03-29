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

import com.arpnetworking.kairos.client.models.Aggregator;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricNamesResponse;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.Sampling;
import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.arpnetworking.kairos.service.KairosDbService;
import com.arpnetworking.kairos.service.QueryContext;
import com.arpnetworking.kairos.service.QueryOrigin;
import com.arpnetworking.testing.SerializationTestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import play.libs.ws.WSClient;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
    private KairosDbService _mockKairosDbService;

    private KairosDbProxyController _controller;
    private AutoCloseable _mocks;

    @Before
    public void setUp() {
        _mocks = MockitoAnnotations.openMocks(this);
        when(_mockConfig.getString(eq("kairosdb.uri"))).thenReturn("http://example.com/");
        when(_mockConfig.getBoolean(eq("kairosdb.proxy.requireAggregators"))).thenReturn(true);
        when(_mockConfig.getBoolean(eq("kairosdb.proxy.addMergeAggregator"))).thenReturn(true);
        when(_mockKairosDbService.queryMetricNames(any(), any(), anyBoolean())).thenReturn(CompletableFuture.completedFuture(
                new MetricNamesResponse.Builder()
                        .setResults(ImmutableList.of("metric1", "metric2"))
                        .build()
        ));
        _controller = new KairosDbProxyController(
                _mockConfig,
                _mockWSClient,
                OBJECT_MAPPER,
                _mockKairosDbService
        );
    }

    @After
    public void tearDown() {
        if (_mocks != null) {
            try {
                _mocks.close();
                // CHECKSTYLE.OFF: IllegalCatch - Ignore all errors when closing the mock
            } catch (final Exception ignored) { }
                // CHECKSTYLE.ON: IllegalCatch
        }
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
        Result result;
        try {
            result = _controller.queryMetrics(request.build()).toCompletableFuture().get(10, TimeUnit.SECONDS);
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }

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

        when(_mockKairosDbService.queryMetrics(any(), any())).thenReturn(
                CompletableFuture.completedFuture(new MetricsQueryResponse.Builder()
                        .setQueries(ImmutableList.of()).build())
        );

        try {
            result = _controller.queryMetrics(request.build()).toCompletableFuture().get(10, TimeUnit.SECONDS);
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }

        assertEquals(Http.Status.OK, result.status());
        assertEquals("{\"queries\":[]}", Helpers.contentAsString(result));
    }

    @Test
    public void testClampsAggregators() throws ExecutionException, InterruptedException, TimeoutException {
        when(_mockConfig.getDuration(eq("kairosdb.proxy.minAggregationPeriod"))).thenReturn(Duration.ofMinutes(1));
        _controller = new KairosDbProxyController(
                _mockConfig,
                _mockWSClient,
                OBJECT_MAPPER,
                _mockKairosDbService
        );
        final Metric.Builder metric1Builder = new Metric.Builder()
                .setName("metric1")
                .setAggregators(ImmutableList.of(new Aggregator.Builder().setName("count")
                        .setSampling(new Sampling.Builder().setValue(1).setUnit(SamplingUnit.SECONDS).build())
                        .build()));
        final Metric.Builder metric2Builder = new Metric.Builder()
                .setName("metric2")
                .setAggregators(ImmutableList.of(new Aggregator.Builder().setName("sum").build()));
        final MetricsQuery.Builder builder = new MetricsQuery.Builder()
                .setStartTime(Instant.now())
                .setMetrics(ImmutableList.of(metric1Builder.build(), metric2Builder.build()));

        metric2Builder.setAggregators(ImmutableList.of(new Aggregator.Builder().setName("sum").build()));
        builder.setMetrics(ImmutableList.of(metric1Builder.build(), metric2Builder.build()));

        final Http.RequestBuilder request = Helpers.fakeRequest()
                .method(Helpers.POST)
                .uri("/api/v1/datapoints/query")
                .header("Content-Type", "application/json")
                .bodyJson(OBJECT_MAPPER.<JsonNode>valueToTree(builder.build()));

        final ArgumentCaptor<MetricsQuery> queryCaptor = ArgumentCaptor.forClass(MetricsQuery.class);

        when(_mockKairosDbService.queryMetrics(any(), queryCaptor.capture())).thenReturn(
                CompletableFuture.completedFuture(new MetricsQueryResponse.Builder().setQueries(ImmutableList.of()).build()));

        _controller.queryMetrics(request.build()).toCompletableFuture().get(10, TimeUnit.SECONDS);


        final MetricsQuery query = queryCaptor.getValue();
        final ImmutableList<Metric> queryMetrics = query.getMetrics();
        assertEquals(2, queryMetrics.size());

        Metric queryMetric = queryMetrics.get(0);
        ImmutableList<Aggregator> aggregators = queryMetric.getAggregators();
        assertEquals(1, aggregators.size());
        Aggregator aggregator = aggregators.get(0);
        assertEquals("count", aggregator.getName());
        final Sampling sampling = aggregator.getSampling().get();
        assertEquals(Duration.ofMinutes(1), Duration.of(sampling.getValue(), SamplingUnit.toChronoUnit(sampling.getUnit())));


        queryMetric = queryMetrics.get(1);
        aggregators = queryMetric.getAggregators();
        aggregator = aggregators.get(0);
        assertEquals(1, aggregators.size());
        assertEquals("sum", aggregator.getName());
    }

    @Test
    public void testNoClampAggregatorWhenNoConfig() throws ExecutionException, InterruptedException, TimeoutException {
        final Metric.Builder metric1Builder = new Metric.Builder()
                .setName("metric1")
                .setAggregators(ImmutableList.of(new Aggregator.Builder().setName("count")
                        .setSampling(new Sampling.Builder().setValue(1).setUnit(SamplingUnit.SECONDS).build())
                        .build()));
        final Metric.Builder metric2Builder = new Metric.Builder()
                .setName("metric2")
                .setAggregators(ImmutableList.of(new Aggregator.Builder().setName("sum").build()));
        final MetricsQuery.Builder builder = new MetricsQuery.Builder()
                .setStartTime(Instant.now())
                .setMetrics(ImmutableList.of(metric1Builder.build(), metric2Builder.build()));

        metric2Builder.setAggregators(ImmutableList.of(new Aggregator.Builder().setName("sum").build()));
        builder.setMetrics(ImmutableList.of(metric1Builder.build(), metric2Builder.build()));

        final Http.RequestBuilder request = Helpers.fakeRequest()
                .method(Helpers.POST)
                .uri("/api/v1/datapoints/query")
                .header("Content-Type", "application/json")
                .bodyJson(OBJECT_MAPPER.<JsonNode>valueToTree(builder.build()));

        final ArgumentCaptor<MetricsQuery> queryCaptor = ArgumentCaptor.forClass(MetricsQuery.class);

        when(_mockKairosDbService.queryMetrics(any(), queryCaptor.capture())).thenReturn(
                CompletableFuture.completedFuture(new MetricsQueryResponse.Builder().setQueries(ImmutableList.of()).build()));


        _controller.queryMetrics(request.build()).toCompletableFuture().get(10, TimeUnit.SECONDS);


        final MetricsQuery query = queryCaptor.getValue();
        final ImmutableList<Metric> queryMetrics = query.getMetrics();
        assertEquals(2, queryMetrics.size());

        Metric queryMetric = queryMetrics.get(0);
        ImmutableList<Aggregator> aggregators = queryMetric.getAggregators();
        assertEquals(1, aggregators.size());
        Aggregator aggregator = aggregators.get(0);
        assertEquals("count", aggregator.getName());
        final Sampling sampling = aggregator.getSampling().get();
        assertEquals(Duration.ofSeconds(1), Duration.of(sampling.getValue(), SamplingUnit.toChronoUnit(sampling.getUnit())));


        queryMetric = queryMetrics.get(1);
        aggregators = queryMetric.getAggregators();
        aggregator = aggregators.get(0);
        assertEquals(1, aggregators.size());
        assertEquals("sum", aggregator.getName());
    }

    @Test
    public void testQueryRequiresAggregatorOff() {
        when(_mockConfig.getBoolean(eq("kairosdb.proxy.requireAggregators"))).thenReturn(false);

        final KairosDbProxyController controller = new KairosDbProxyController(
                _mockConfig,
                _mockWSClient,
                OBJECT_MAPPER,
                _mockKairosDbService
        );

        final Metric.Builder metric1Builder = new Metric.Builder()
                .setName("metric1")
                .setAggregators(ImmutableList.of(new Aggregator.Builder().setName("count").build()));
        final Metric.Builder metric2Builder = new Metric.Builder()
                .setName("metric2");
        final MetricsQuery.Builder builder = new MetricsQuery.Builder()
                .setStartTime(Instant.now())
                .setMetrics(ImmutableList.of(metric1Builder.build(), metric2Builder.build()));

        mockQueryMetrics(controller, builder.build(), builder.build());
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
                OBJECT_MAPPER,
                _mockKairosDbService
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

    private void mockQueryMetrics(final KairosDbProxyController controller,
                                  final MetricsQuery metricsQuery,
                                  final MetricsQuery newMetricsQuery) {
        final Http.RequestBuilder request = play.test.Helpers.fakeRequest()
                .method(play.test.Helpers.POST)
                .uri("/api/v1/datapoints/query")
                .header("Content-Type", "application/json")
                .bodyJson(OBJECT_MAPPER.<JsonNode>valueToTree(metricsQuery));

        // Round trip through JSON so that the mock matcher actually matches
        final MetricsQuery converted = OBJECT_MAPPER.convertValue(newMetricsQuery, MetricsQuery.class);

        final ArgumentCaptor<QueryContext> contextCaptor = ArgumentCaptor.forClass(QueryContext.class);
        when(_mockKairosDbService.queryMetrics(contextCaptor.capture(), eq(converted))).thenReturn(
                CompletableFuture.completedFuture(new MetricsQueryResponse.Builder()
                        .setQueries(ImmutableList.of()).build())
        );

        final Result result;
        try {
            result = controller.queryMetrics(request.build()).toCompletableFuture().get(10, TimeUnit.SECONDS);
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }

        assertEquals(Http.Status.OK, result.status());
        assertEquals("{\"queries\":[]}", Helpers.contentAsString(result));
        assertEquals(contextCaptor.getValue().getOrigin(), QueryOrigin.EXTERNAL_REQUEST);
    }

    @Test
    public void testCheckAndAddMergeAggregator() {
        // Test case where the first aggregator doesn't have a sampling
        final Metric metric1 = new Metric.Builder()
                .setName("metric1")
                .setAggregators(ImmutableList.of(
                        new Aggregator.Builder().setName("filter").build(),
                        new Aggregator.Builder().setName("max").setSampling(
                                new Sampling.Builder().setValue(1).setUnit(SamplingUnit.HOURS).build()).build()))
                .build();
        // Test case where the merge aggregator is not needed
        final Metric metric2 = new Metric.Builder()
                .setName("metric2")
                .setAggregators(ImmutableList.of(new Aggregator.Builder().setName("min").build()))
                .build();
        // Test case where top aggregator with sampling has non-merge specific attributes
        final Metric metric3 = new Metric.Builder()
                .setName("metric3")
                .setAggregators(ImmutableList.of(new Aggregator.Builder().setName("hpercentile").setSampling(
                        new Sampling.Builder().setValue(1).setUnit(SamplingUnit.HOURS).build()).setAlignStartTime(
                                true).addOtherArg("percentile", 0.5).build()))
                .build();
        // Test case where top aggregator with sampling is moving window
        final Metric metric4 = new Metric.Builder()
                .setName("metric4")
                .setAggregators(ImmutableList.of(new Aggregator.Builder().setName("movingWindow").setSampling(
                        new Sampling.Builder().setValue(7).setUnit(SamplingUnit.DAYS).build()).setAlignStartTime(
                                true).build()))
                .build();
        final MetricsQuery metricsQuery = new MetricsQuery.Builder()
                .setStartTime(Instant.now())
                .setMetrics(ImmutableList.of(metric1, metric2, metric3, metric4))
                .build();

        final MetricsQuery newMetricsQuery = _controller.checkAndAddMergeAggregator(metricsQuery);
        assertEquals("merge", newMetricsQuery.getMetrics().get(0).getAggregators().get(0).getName());
        assertEquals("filter", newMetricsQuery.getMetrics().get(0).getAggregators().get(1).getName());
        assertEquals("max", newMetricsQuery.getMetrics().get(0).getAggregators().get(2).getName());
        assertEquals(newMetricsQuery.getMetrics().get(0).getAggregators().get(2).getSampling(),
                newMetricsQuery.getMetrics().get(0).getAggregators().get(0).getSampling());
        assertEquals("min", newMetricsQuery.getMetrics().get(1).getAggregators().get(0).getName());
        assertEquals("merge", newMetricsQuery.getMetrics().get(2).getAggregators().get(0).getName());
        assertEquals("hpercentile", newMetricsQuery.getMetrics().get(2).getAggregators().get(1).getName());
        assertEquals(newMetricsQuery.getMetrics().get(2).getAggregators().get(1).getSampling(),
                newMetricsQuery.getMetrics().get(2).getAggregators().get(0).getSampling());
        assertEquals(newMetricsQuery.getMetrics().get(2).getAggregators().get(1).getAlignStartTime(),
                newMetricsQuery.getMetrics().get(2).getAggregators().get(0).getAlignStartTime());
        assertTrue(newMetricsQuery.getMetrics().get(2).getAggregators().get(0).getOtherArgs().isEmpty());
        assertEquals("merge", newMetricsQuery.getMetrics().get(3).getAggregators().get(0).getName());
        assertEquals("movingWindow", newMetricsQuery.getMetrics().get(3).getAggregators().get(1).getName());
        assertEquals(1,
                newMetricsQuery.getMetrics().get(3).getAggregators().get(0).getSampling().get().getValue());
        assertEquals(newMetricsQuery.getMetrics().get(3).getAggregators().get(1).getSampling().get().getUnit(),
                newMetricsQuery.getMetrics().get(3).getAggregators().get(0).getSampling().get().getUnit());
    }
}
