/*
 * Copyright 2019 Inscope Metrics
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
package com.arpnetworking.metrics.portal.integration.controllers;

import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.KairosDbClientImpl;
import com.arpnetworking.kairos.client.models.Aggregator;
import com.arpnetworking.kairos.client.models.DataPoint;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricDataPoints;
import com.arpnetworking.kairos.client.models.MetricTags;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.Sampling;
import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.arpnetworking.kairos.client.models.TagsQuery;
import com.arpnetworking.metrics.impl.NoOpMetricsFactory;
import com.arpnetworking.metrics.portal.integration.test.WebServerHelper;
import com.arpnetworking.testing.SerializationTestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.pekko.actor.ActorSystem;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import play.mvc.Http;
import scala.concurrent.duration.FiniteDuration;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for {@code KairosDbProxyController}.
 *
 * @author Ville Koskela (ville at inscopemetrics dot io)
 */
public final class KairosDbProxyControllerIT {

    @Before
    public void setUp() {
        final StringBuilder urlBuilder = new StringBuilder("http://");
        urlBuilder.append(getEnvOrDefault("KAIROSDB_HOST", "localhost"));
        urlBuilder.append(":");
        urlBuilder.append(getEnvOrDefault("KAIROSDB_PORT", "8082"));

        _kairosDbClientDirect = new KairosDbClientImpl.Builder()
                .setActorSystem(PEKKO_ACTOR_SYSTEM)
                .setMapper(SerializationTestUtils.getApiObjectMapper())
                .setReadTimeout(FiniteDuration.apply(10, TimeUnit.SECONDS))
                .setUri(URI.create(urlBuilder.toString()))
                .setMetricsFactory(new NoOpMetricsFactory())
                .build();

        _kairosDbClientProxied = new KairosDbClientImpl.Builder()
                .setActorSystem(PEKKO_ACTOR_SYSTEM)
                .setMapper(SerializationTestUtils.getApiObjectMapper())
                .setReadTimeout(FiniteDuration.apply(10, TimeUnit.SECONDS))
                .setUri(URI.create(WebServerHelper.getUri("")))
                .setMetricsFactory(new NoOpMetricsFactory())
                .build();

        // Record data on the minute, 30 minutes back to make it easy to fetch via the KDB UI
        _start = Instant.now().truncatedTo(ChronoUnit.MINUTES).minusSeconds(30 * 60 * 60);
        _metricName = "m_" + UUID.randomUUID().toString();
    }

    @Test
    public void testHealthStatus() throws IOException {
        final HttpGet request = new HttpGet(WebServerHelper.getUri("/api/v1/health/status"));

        try (CloseableHttpResponse response = WebServerHelper.getClient().execute(request)) {
            assertEquals(Http.Status.OK, response.getStatusLine().getStatusCode());
            assertEquals(ContentType.APPLICATION_JSON.getMimeType(), response.getLastHeader(HttpHeaders.CONTENT_TYPE).getValue());

            final JsonNode pingJson = WebServerHelper.readContentAsJson(response);
            assertEquals("JVM-Thread-Deadlock: OK", pingJson.get(0).asText());
            assertEquals("Datastore-Query: OK", pingJson.get(1).asText());
        }
    }

    @Test
    public void testSimplePersistAndQuery() throws ExecutionException, InterruptedException {
        _kairosDbClientDirect.addDataPoints(ImmutableList.of(
                new MetricDataPoints.Builder()
                        .setName(_metricName)
                        // NOTE: KDB requires every metric to have at least one tag!
                        .setTags(ImmutableMap.of("host", "host.example.com"))
                        .setDatapoints(ImmutableList.of(
                                new DataPoint.Builder()
                                        .setValue(10)
                                        .setTime(_start)
                                        .build(),
                                new DataPoint.Builder()
                                        .setValue(20)
                                        .setTime(_start.plusSeconds(60))
                                        .build(),
                                new DataPoint.Builder()
                                        .setValue(30)
                                        .setTime(_start.plusSeconds(120))
                                        .build()))
                        .build())).toCompletableFuture().get();

        assertMetricsQuery("sum", 60, 2);
        assertMetricsQuery("count", 3, 2);
        assertMetricsQuery("avg", 20, 2);
    }

    @Test
    public void testTagQuery() throws ExecutionException, InterruptedException {
        // NOTE: The "ignored" tag is configured into the running Metrics Portal
        // instance via the environment variable in the Docker launch configuration
        // in the pom.xml file (since we don't want this in the base configuration).
        _kairosDbClientDirect.addDataPoints(ImmutableList.of(
                new MetricDataPoints.Builder()
                        .setName(_metricName)
                        .setTags(ImmutableMap.of(
                                "host", "host.example.com",
                                "os", "windows",
                                "ignored", "foo"))
                        .setDatapoints(ImmutableList.of(
                                new DataPoint.Builder()
                                        .setValue(10)
                                        .setTime(_start)
                                        .build()))
                        .build())).toCompletableFuture().get();

        final MetricsQueryResponse response = _kairosDbClientProxied.queryMetricTags(new TagsQuery.Builder()
                .setStartTime(_start)
                //.setEndTime(_start.plusSeconds(61))
                .setMetrics(ImmutableList.of(new MetricTags.Builder()
                        .setName(_metricName)
                        .build()))
                .build()).toCompletableFuture().get();

        assertEquals(1, response.getQueries().size());
        assertEquals(1, response.getQueries().get(0).getResults().size());

        final MetricsQueryResponse.QueryResult result = response.getQueries().get(0).getResults().get(0);
        assertEquals(_metricName, result.getName());
        assertTrue(result.getGroupBy().isEmpty());
        assertEquals(2, result.getTags().size());
        assertEquals(ImmutableList.of("host.example.com"), result.getTags().get("host"));
        assertEquals(ImmutableList.of("windows"), result.getTags().get("os"));
        assertTrue(result.getValues().isEmpty());
    }

    @Test
    public void testQueryMetricsExcludeFilterTagInResponse() throws ExecutionException, InterruptedException {
        _kairosDbClientDirect.addDataPoints(ImmutableList.of(
                new MetricDataPoints.Builder()
                        .setName(_metricName)
                        // NOTE: KDB requires every metric to have at least one tag!
                        .setTags(ImmutableMap.of(
                                "host", "host.example.com",
                                "ignored", "foo"))
                        .setDatapoints(ImmutableList.of(
                                new DataPoint.Builder()
                                        .setValue(10)
                                        .setTime(_start)
                                        .build()))
                        .build())).toCompletableFuture().get();

        assertMetricsQuery("count", 1, 1);
    }

    @Test
    public void testQueryMetricsExcludedFilterTagRetainedInResponse() throws ExecutionException, InterruptedException {
        _kairosDbClientDirect.addDataPoints(ImmutableList.of(
                new MetricDataPoints.Builder()
                        .setName(_metricName)
                        // NOTE: KDB requires every metric to have at least one tag!
                        .setTags(ImmutableMap.of(
                                "host", "host.example.com",
                                "ignored", "foo"))
                        .setDatapoints(ImmutableList.of(
                                new DataPoint.Builder()
                                        .setValue(10)
                                        .setTime(_start)
                                        .build()))
                        .build())).toCompletableFuture().get();

        final MetricsQuery query = new MetricsQuery.Builder()
                .setStartTime(_start)
                .setEndTime(_start.plusSeconds(61))
                .setMetrics(ImmutableList.of(new Metric.Builder()
                        .setName(_metricName)
                        .setTags(ImmutableMultimap.of("ignored", "foo"))
                        .setAggregators(ImmutableList.of(new Aggregator.Builder()
                                .setName("count")
                                .setSampling(new Sampling.Builder()
                                        .setUnit(SamplingUnit.SECONDS)
                                        .setValue(120)
                                        .build())
                                .build()))
                        .build()))
                .build();

        final MetricsQueryResponse response = queryMetrics(query);

        assertEquals(1, response.getQueries().size());
        assertEquals(1, response.getQueries().get(0).getResults().size());

        final MetricsQueryResponse.QueryResult result = response.getQueries().get(0).getResults().get(0);
        assertEquals(_metricName, result.getName());
        assertEquals(1, result.getGroupBy().size());
        assertEquals(2, result.getTags().size());
        assertEquals(ImmutableList.of("host.example.com"), result.getTags().get("host"));
        assertEquals(ImmutableList.of("foo"), result.getTags().get("ignored"));
        assertEquals(1, result.getValues().size());
        assertEquals(1, result.getValues().get(0).getValue().get());
    }

    @Test
    public void testQueryMetricsExcludedGroupByTagRetainedInResponse() throws ExecutionException, InterruptedException {
        _kairosDbClientDirect.addDataPoints(ImmutableList.of(
                new MetricDataPoints.Builder()
                        .setName(_metricName)
                        // NOTE: KDB requires every metric to have at least one tag!
                        .setTags(ImmutableMap.of(
                                "host", "host.example.com",
                                "ignored", "foo"))
                        .setDatapoints(ImmutableList.of(
                                new DataPoint.Builder()
                                        .setValue(10)
                                        .setTime(_start)
                                        .build()))
                        .build())).toCompletableFuture().get();

        final MetricsQuery query = new MetricsQuery.Builder()
                .setStartTime(_start)
                .setEndTime(_start.plusSeconds(61))
                .setMetrics(ImmutableList.of(new Metric.Builder()
                        .setName(_metricName)
                        .setGroupBy(ImmutableList.of(
                                new MetricsQuery.QueryTagGroupBy.Builder()
                                        .setTags(ImmutableSet.of("ignored"))
                                        .build()))
                        .setAggregators(ImmutableList.of(new Aggregator.Builder()
                                .setName("count")
                                .setSampling(new Sampling.Builder()
                                        .setUnit(SamplingUnit.SECONDS)
                                        .setValue(120)
                                        .build())
                                .build()))
                        .build()))
                .build();

        final MetricsQueryResponse response = queryMetrics(query);

        assertEquals(1, response.getQueries().size());
        assertEquals(1, response.getQueries().get(0).getResults().size());

        final MetricsQueryResponse.QueryResult result = response.getQueries().get(0).getResults().get(0);
        assertEquals(_metricName, result.getName());
        assertEquals(2, result.getGroupBy().size());
        assertThat(result.getGroupBy().get(0), Matchers.instanceOf(MetricsQueryResponse.QueryTagGroupBy.class));
        final MetricsQueryResponse.QueryTagGroupBy groupBy = (MetricsQueryResponse.QueryTagGroupBy) result.getGroupBy().get(0);
        assertEquals(1, groupBy.getGroup().size());
        assertEquals("foo", groupBy.getGroup().get("ignored"));
        assertEquals(1, groupBy.getTags().size());
        assertEquals("ignored", groupBy.getTags().get(0));
        assertEquals(1, result.getTags().size());
        assertEquals(ImmutableList.of("host.example.com"), result.getTags().get("host"));
        assertEquals(1, result.getValues().size());
        assertEquals(1, result.getValues().get(0).getValue().get());
    }

    private void assertMetricsQuery(final String aggregator, final Number expectedValue, final int minutes) {
        final MetricsQueryResponse response = queryMetrics(createMetricsQuery(aggregator, minutes));

        assertEquals(1, response.getQueries().size());
        assertEquals(1, response.getQueries().get(0).getResults().size());

        final MetricsQueryResponse.QueryResult result = response.getQueries().get(0).getResults().get(0);
        assertEquals(createError("Unexpected metric name"), _metricName, result.getName());
        assertEquals(createError("Unexpected group by"), 1, result.getGroupBy().size());
        assertEquals(createError("Unexpected tags"), 1, result.getTags().size());
        assertEquals(createError("Unexpected tag"), ImmutableList.of("host.example.com"), result.getTags().get("host"));
        assertEquals(createError("Unexpected values for " + aggregator), 1, result.getValues().size());
        assertEquals(createError("Wrong value for " + aggregator), expectedValue, result.getValues().get(0).getValue().get());
    }

    private MetricsQueryResponse queryMetrics(final MetricsQuery query) {
        try {
            return _kairosDbClientProxied.queryMetrics(query)
                    .toCompletableFuture().get();
        } catch (final ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private MetricsQuery createMetricsQuery(final String aggregator, final int minutes) {
            return new MetricsQuery.Builder()
                    .setStartTime(_start)
                    .setEndTime(_start.plusSeconds(60 * minutes + 1))
                    .setMetrics(ImmutableList.of(new Metric.Builder()
                            .setName(_metricName)
                            .setAggregators(ImmutableList.of(new Aggregator.Builder()
                                    .setName(aggregator)
                                    .setSampling(new Sampling.Builder()
                                            .setUnit(SamplingUnit.SECONDS)
                                            .setValue((minutes + 1) * 60)
                                            .build())
                                    .build()))
                            .build()))
                    .build();
    }

    private String createError(final String message) {
        return String.format("%s (metric=%s, start=%s)", message, _metricName, _start);
    }

    private static String getEnvOrDefault(final String name, final String defaultValue) {
        @Nullable final String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }

    private Instant _start;
    private String _metricName;
    private KairosDbClient _kairosDbClientProxied;
    private KairosDbClient _kairosDbClientDirect;

    private static final ActorSystem PEKKO_ACTOR_SYSTEM = ActorSystem.create(KairosDbProxyControllerIT.class.getSimpleName());
}
