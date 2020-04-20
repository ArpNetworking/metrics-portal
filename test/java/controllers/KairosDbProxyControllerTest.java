package controllers;

import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.*;
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
import static play.mvc.Http.Status.BAD_REQUEST;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.POST;

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

    private KairosDbProxyController controller;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(_mockConfig.getString(eq("kairosdb.uri"))).thenReturn("http://example.com/");
        when(_mockMetricsFactory.create()).thenReturn(_mockMetrics);
        when(_mockMetrics.createTimer(any())).thenReturn(_mockTimer);
        when(_mockKairosDbClient.queryMetricNames()).thenReturn(CompletableFuture.completedFuture(
                new MetricNamesResponse.Builder()
                        .setResults(ImmutableList.of("metric1", "metric2"))
                        .build()
        ));

        controller = new KairosDbProxyController(
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
        Metric.Builder metric1Builder = new Metric.Builder()
                .setName("metric1")
                .setAggregators(ImmutableList.of(new Aggregator.Builder().setName("count").build()));
        Metric.Builder metric2Builder = new Metric.Builder()
                .setName("metric2");
        MetricsQuery.Builder builder = new MetricsQuery.Builder()
                .setStartTime(Instant.now())
                .setMetrics(ImmutableList.of(metric1Builder.build(), metric2Builder.build()));

        Http.RequestBuilder request = Helpers.fakeRequest()
                .method(POST)
                .uri("/api/v1/datapoints/query")
                .header("Content-Type", "application/json")
                .bodyJson(OBJECT_MAPPER.<JsonNode>valueToTree(builder.build()));

        // ***
        // Test failure case where one metric doesn't have an aggregator
        // ***
        Result result = Helpers.invokeWithContext(request, Helpers.contextComponents(), () -> {
            CompletionStage<Result> completionStage = controller.queryMetrics();
            return completionStage.toCompletableFuture().get(10, TimeUnit.SECONDS);
        });

        assertEquals(BAD_REQUEST, result.status());
        assertEquals("All queried metrics must have at least one aggregator",
                Helpers.contentAsString(result));

        // ***
        // Test success case where both metrics have aggregators
        // ***
        metric2Builder.setAggregators(ImmutableList.of(new Aggregator.Builder().setName("sum").build()));
        builder.setMetrics(ImmutableList.of(metric1Builder.build(), metric2Builder.build()));

        request = Helpers.fakeRequest()
                .method(POST)
                .uri("/api/v1/datapoints/query")
                .header("Content-Type", "application/json")
                .bodyJson(OBJECT_MAPPER.<JsonNode>valueToTree(builder.build()));

        when(_mockKairosDbClient.queryMetrics(any())).thenReturn(
                CompletableFuture.completedFuture(new MetricsQueryResponse.Builder()
                        .setQueries(ImmutableList.of()).build())
        );
        result = Helpers.invokeWithContext(request, Helpers.contextComponents(), () -> {
            CompletionStage<Result> completionStage = controller.queryMetrics();
            return completionStage.toCompletableFuture().get(10, TimeUnit.SECONDS);
        });

        assertEquals(OK, result.status());
        assertEquals("{\"queries\":[]}", Helpers.contentAsString(result));
    }
}
