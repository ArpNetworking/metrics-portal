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
import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.kairos.client.models.Aggregator;
import com.arpnetworking.kairos.client.models.KairosMetricNamesQueryResponse;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.Rollup;
import com.arpnetworking.kairos.client.models.RollupQuery;
import com.arpnetworking.kairos.client.models.RollupResponse;
import com.arpnetworking.kairos.client.models.RollupTask;
import com.arpnetworking.kairos.client.models.Sampling;
import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import io.ebeaninternal.util.IOUtils;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import scala.concurrent.duration.FiniteDuration;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
    private static final String CLASS_NAME = KairosDbClientImplTest.class.getSimpleName();

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
        final KairosMetricNamesQueryResponse response = _kairosDbClient.queryMetricNames().toCompletableFuture().get();
        Assert.assertThat(response.getResults(), Matchers.contains("foo"));
    }

    @Test
    public void testQueryMetric() throws Exception {
        final Instant now = Instant.now();

        _wireMock.givenThat(
                post(urlEqualTo(KairosDbClientImpl.METRICS_QUERY_PATH.toString()))
                        .withRequestBody(equalToJson(readResource("testQueryMetric"), true, true))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(readResource("testQueryMetric.response"))
                        )
        );

        final MetricsQueryResponse response = _kairosDbClient.queryMetrics(new MetricsQuery.Builder()
                .setStartTime(now)
                .setMetrics(
                        ImmutableList.of(new Metric.Builder()
                                .setName("metric.name")
                                .setTags(ImmutableMultimap.of("tag1", "tag1", "tag2", "tag2"))
                                .setAggregators(ImmutableList.of(new Aggregator.Builder()
                                        .setName("avg")
                                        .setAlignSampling(Optional.of(Boolean.TRUE))
                                        .setSampling(Optional.of(new Sampling.Builder().setValue(1).setUnit(SamplingUnit.DAYS).build()))
                                        .build()
                                ))
                                .setLimit(1)
                                .setOrder(Metric.Order.DESC)
                                .build()
                        )
                )
                .build()
        ).toCompletableFuture().get();

        JSONAssert.assertEquals(readResource("testQueryMetric.response"),
                OBJECT_MAPPER.writeValueAsString(response), JSONCompareMode.STRICT);
    }

    @Test
    public void testQueryMetricTags() throws Exception {
        _wireMock.givenThat(
                post(urlEqualTo(KairosDbClientImpl.METRICS_TAGS_PATH.toString()))
                        .withRequestBody(equalToJson(readResource("testQueryMetricTags"), true, true))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(readResource("testQueryMetricTags.response"))
                        )
        );

        final MetricsQueryResponse response = _kairosDbClient.queryMetricTags(new MetricsQuery.Builder()
                .setStartTime(Instant.ofEpochSecond(0))
                .setMetrics(
                        ImmutableList.of(new Metric.Builder()
                                .setName("metric.name")
                                .build()
                        )
                )
                .build()
        ).toCompletableFuture().get();

        JSONAssert.assertEquals(readResource("testQueryMetricTags.response"),
                OBJECT_MAPPER.writeValueAsString(response), JSONCompareMode.LENIENT);
    }

    @Test
    public void testRollupsList() throws Exception {
        _wireMock.givenThat(
                get(urlEqualTo(KairosDbClientImpl.ROLLUPS_PATH.toString()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(readResource("testRollupsList.response"))
                        )
        );

        final List<RollupTask> response = _kairosDbClient.queryRollups().toCompletableFuture().get();
        JSONAssert.assertEquals(readResource("testRollupsList.response"),
                OBJECT_MAPPER.writeValueAsString(response), JSONCompareMode.LENIENT);
    }

    @Test
    public void testCreateRollup() throws Exception {
        _wireMock.givenThat(
                post(urlEqualTo(KairosDbClientImpl.ROLLUPS_PATH.toString()))
                        .withRequestBody(equalToJson(readResource("testCreateRollup"), true, false))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(readResource("testCreateRollup.response"))
                        )
        );

        final RollupResponse response = _kairosDbClient.createRollup(new RollupTask.Builder()
                .setExecutionInterval(new Sampling.Builder().setValue(1).setUnit(SamplingUnit.DAYS).build())
                .setName("testRollup")
                .setRollups(
                        ImmutableList.of(new Rollup.Builder()
                                .setSaveAs("testMetric_1d")
                                .setQuery(new RollupQuery.Builder()
                                        .setStartRelative(new Sampling.Builder().setValue(1).setUnit(SamplingUnit.DAYS).build())
                                        .setMetrics(
                                                ImmutableList.of(new Metric.Builder()
                                                        .setName("testMetric")
                                                        .setAggregators(ImmutableList.of(new Aggregator.Builder()
                                                                        .setName("merge")
                                                                        .setAlignSampling(Optional.of(Boolean.TRUE))
                                                                        .setOtherArgs(ImmutableMap.of(
                                                                                "align_start_time", Boolean.TRUE,
                                                                                "align_end_time", Boolean.FALSE))
                                                                        .setSampling(
                                                                                Optional.of(
                                                                                        new Sampling.Builder()
                                                                                                .setValue(1)
                                                                                                .setUnit(SamplingUnit.DAYS)
                                                                                                .build()
                                                                                )
                                                                        )
                                                                        .build()
                                                                )
                                                        )
                                                        .setGroupBy(ImmutableList.of(new MetricsQuery.GroupBy.Builder()
                                                                        .setName("tag")
                                                                        .addOtherArg("tags", ImmutableList.of("foo", "bar"))
                                                                        .build()
                                                                )
                                                        )
                                                        .build()
                                                )
                                        )
                                        .build()
                                )
                                .build()
                        )
                )
                .build()
        ).toCompletableFuture().get();

        JSONAssert.assertEquals(readResource("testCreateRollup.response"),
                OBJECT_MAPPER.writeValueAsString(response), JSONCompareMode.STRICT);
    }

    @Test
    public void testUpdateRollup() throws Exception {
        final String id = "rollup_id";
        _wireMock.givenThat(
                put(urlEqualTo(KairosDbClientImpl.ROLLUPS_PATH.toString() + "/" + id))
                        .withRequestBody(equalToJson(readResource("testUpdateRollup"), true, false))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(readResource("testUpdateRollup.response"))
                        )
        );

        final RollupResponse response = _kairosDbClient.updateRollup(id, new RollupTask.Builder()
                .setExecutionInterval(new Sampling.Builder().setValue(1).setUnit(SamplingUnit.DAYS).build())
                .setName("testRollup")
                .setId(id)
                .setRollups(
                        ImmutableList.of(new Rollup.Builder()
                                .setSaveAs("testMetric_1d")
                                .setQuery(new RollupQuery.Builder()
                                        .setStartRelative(new Sampling.Builder().setValue(1).setUnit(SamplingUnit.DAYS).build())
                                        .setMetrics(
                                                ImmutableList.of(new Metric.Builder()
                                                        .setName("testMetric")
                                                        .setAggregators(ImmutableList.of(new Aggregator.Builder()
                                                                        .setName("merge")
                                                                        .setAlignSampling(Optional.of(Boolean.TRUE))
                                                                        .setOtherArgs(ImmutableMap.of(
                                                                                "align_start_time", Boolean.TRUE,
                                                                                "align_end_time", Boolean.FALSE))
                                                                        .setSampling(
                                                                                Optional.of(
                                                                                        new Sampling.Builder()
                                                                                                .setValue(1)
                                                                                                .setUnit(SamplingUnit.DAYS)
                                                                                                .build()
                                                                                )
                                                                        )
                                                                        .build()
                                                                )
                                                        )
                                                        .setGroupBy(ImmutableList.of(new MetricsQuery.GroupBy.Builder()
                                                                        .setName("tag")
                                                                        .addOtherArg("tags", ImmutableList.of("foo", "bar"))
                                                                        .build()
                                                                )
                                                        )
                                                        .build()
                                                )
                                        )
                                        .build()
                                )
                                .build()
                        )
                )
                .build()
        ).toCompletableFuture().get();

        JSONAssert.assertEquals(readResource("testUpdateRollup.response"),
                OBJECT_MAPPER.writeValueAsString(response), JSONCompareMode.STRICT);
    }

    @Test
    public void testDeleteRollup() throws Exception {
        final String id = "rollup_id";
        _wireMock.givenThat(
                delete(urlEqualTo(KairosDbClientImpl.ROLLUPS_PATH.toString() + "/" + id))
                        .willReturn(aResponse().withStatus(204))
        );

        final Void response = _kairosDbClient.deleteRollup(id).toCompletableFuture().get();
        assertNull(response);
    }

    @Test
    public void testDeleteMissingRollup() {
        final String id = "rollup_id";
        _wireMock.givenThat(
                delete(urlEqualTo(KairosDbClientImpl.ROLLUPS_PATH.toString() + "/" + id))
                        .willReturn(aResponse().withStatus(404))
        );

        Void response = null;
        try {
            response = _kairosDbClient.deleteRollup(id).toCompletableFuture().get();
            fail("Expected KairosDbRequestException to be thrown");
        } catch (final InterruptedException | ExecutionException e) {
            assertTrue(e.getCause() instanceof KairosDbRequestException);
        }
        assertNull(response);
    }

    private String readResource(final String resourceSuffix) {
        try {
            return IOUtils.readUtf8(getClass()
                    .getClassLoader()
                    .getResourceAsStream("com/arpnetworking/kairos/client/" + CLASS_NAME + "." + resourceSuffix + ".json"));
        } catch (final IOException e) {
            fail("Failed with exception: " + e);
            return null;
        }
    }
}
