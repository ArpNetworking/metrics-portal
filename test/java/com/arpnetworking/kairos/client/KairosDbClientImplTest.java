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
package com.arpnetworking.kairos.client;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import com.arpnetworking.kairos.client.models.Aggregator;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricNamesResponse;
import com.arpnetworking.kairos.client.models.MetricTags;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.Sampling;
import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.arpnetworking.kairos.client.models.TagNamesResponse;
import com.arpnetworking.kairos.client.models.TagsQuery;
import com.arpnetworking.testing.SerializationTestUtils;
import com.arpnetworking.utility.test.ResourceHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Test cases for KairosDbClientImpl.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public class KairosDbClientImplTest {
    @Rule
    public WireMockRule _wireMock = new WireMockRule(wireMockConfig().dynamicPort());

    private URI _baseURI;
    private ActorSystem _actorSystem;
    private KairosDbClientImpl _kairosDbClient;

    private static final ObjectMapper OBJECT_MAPPER = SerializationTestUtils.getApiObjectMapper();

    @Before
    public void setUp() throws Exception {
        _baseURI = new URI("http://localhost:" + _wireMock.port());
        _actorSystem = ActorSystem.create();
        _kairosDbClient = new KairosDbClientImpl.Builder()
                .setUri(_baseURI)
                .setActorSystem(_actorSystem)
                .setMapper(OBJECT_MAPPER)
                .setReadTimeout(new FiniteDuration(30, TimeUnit.SECONDS))
                .build();
    }

    @After
    public void tearDown() {
        TestKit.shutdownActorSystem(_actorSystem);
    }

    @Test
    public void testQueryMetricNames() throws Exception {
        _wireMock.givenThat(
                get(urlEqualTo(KairosDbClientImpl.METRICS_NAMES_PATH.toString()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"results\":[\"foo\"]}")
                        )
        );
        final MetricNamesResponse response = _kairosDbClient.queryMetricNames().toCompletableFuture().get();
        Assert.assertThat(response.getResults(), Matchers.contains("foo"));
    }

    @Test
    public void testQueryMetric() throws Exception {
        _wireMock.givenThat(
                post(urlEqualTo(KairosDbClientImpl.METRICS_QUERY_PATH.toString()))
                        .withRequestBody(equalToJson(ResourceHelper.loadResource(getClass(), "testQueryMetric"), true, true))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(ResourceHelper.loadResource(getClass(), "testQueryMetric.response"))
                        )
        );

        final MetricsQueryResponse response = _kairosDbClient.queryMetrics(new MetricsQuery.Builder()
                .setStartTime(Instant.parse("2019-02-01T00:00:00Z"))
                .setEndTime(Instant.parse("2019-02-02T00:00:00Z"))
                .setMetrics(
                        ImmutableList.of(new Metric.Builder()
                                .setName("metric.name")
                                .setTags(ImmutableMultimap.of("tag1", "tag1", "tag2", "tag2"))
                                .setAggregators(ImmutableList.of(new Aggregator.Builder()
                                        .setName("avg")
                                        .setAlignSampling(true)
                                        .setSampling(new Sampling.Builder().setValue(1).setUnit(SamplingUnit.DAYS).build())
                                        .build()
                                ))
                                .setLimit(1)
                                .setOrder(Metric.Order.DESC)
                                .build()
                        )
                )
                .build()
        ).toCompletableFuture().get();

        SerializationTestUtils.assertJsonEquals(
                ResourceHelper.loadResource(getClass(), "testQueryMetric.response"),
                OBJECT_MAPPER.writeValueAsString(response)
        );
    }

    @Test
    public void testQueryMetricTags() throws Exception {
        _wireMock.givenThat(
                post(urlEqualTo(KairosDbClientImpl.TAGS_QUERY_PATH.toString()))
                        .withRequestBody(equalToJson(ResourceHelper.loadResource(getClass(), "testQueryMetricTags"), true, true))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(ResourceHelper.loadResource(getClass(), "testQueryMetricTags.response"))
                        )
        );

        final MetricsQueryResponse response = _kairosDbClient.queryMetricTags(new TagsQuery.Builder()
                .setStartTime(Instant.ofEpochSecond(0))
                .setMetrics(
                        ImmutableList.of(new MetricTags.Builder()
                                .setName("metric.name")
                                .build()
                        )
                )
                .build()
        ).toCompletableFuture().get();

        SerializationTestUtils.assertJsonEquals(
                ResourceHelper.loadResource(getClass(), "testQueryMetricTags.response"),
                OBJECT_MAPPER.writeValueAsString(response)
        );
    }

    @Test
    public void testListTagNames() throws Exception {
        _wireMock.givenThat(
                get(urlEqualTo(KairosDbClientImpl.LIST_TAG_NAMES_PATH.toString()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(ResourceHelper.loadResource(getClass(), "testListTagNames.response"))
                                .withStatus(200))
        );

        final TagNamesResponse response = _kairosDbClient.listTagNames().toCompletableFuture().get();

        SerializationTestUtils.assertJsonEquals(
                ResourceHelper.loadResource(getClass(), "testListTagNames.response"),
                OBJECT_MAPPER.writeValueAsString(response)
        );
    }
}
