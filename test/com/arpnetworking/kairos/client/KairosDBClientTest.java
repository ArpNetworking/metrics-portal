package com.arpnetworking.kairos.client;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.kairos.client.models.Aggregator;
import com.arpnetworking.kairos.client.models.KairosMetricNamesQueryResponse;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.Sampling;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import io.ebeaninternal.util.IOUtils;
import org.hamcrest.Matchers;
import org.joda.time.DateTime;
import org.joda.time.Period;
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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.fail;

public class KairosDBClientTest {
    @Rule
    public WireMockRule _wireMock = new WireMockRule(wireMockConfig().dynamicPort());

    private URI _baseURI;
    private ActorSystem _actorSystem;
    private KairosDbClient _kairosDbClient;

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
    private static final String CLASS_NAME = KairosDBClientTest.class.getSimpleName();

    @Before
    public void setUp() throws Exception {
        _baseURI = new URI("http://localhost:" + _wireMock.port());
        _actorSystem = ActorSystem.create();
        _kairosDbClient = new KairosDbClient.Builder()
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
                get(urlEqualTo(KairosDbClient.METRICS_NAMES_PATH.toString()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"results\":[\"foo\"]}")
                        )
                );
        KairosMetricNamesQueryResponse response = _kairosDbClient.queryMetricNames().toCompletableFuture().get();
        Assert.assertThat(response.getResults(), Matchers.contains("foo"));
    }

    @Test
    public void testQueryMetric() throws Exception {
        DateTime now = DateTime.now();

        _wireMock.givenThat(
                post(urlEqualTo(KairosDbClient.METRICS_QUERY_PATH.toString()))
                .withRequestBody(equalToJson(readResource("testQueryMetric"), true, true))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(readResource("testQueryMetric.response"))
                )
        );

        MetricsQueryResponse response = _kairosDbClient.queryMetrics(new MetricsQuery.Builder()
                .setStartTime(now)
                .setMetrics(
                        ImmutableList.of(new Metric.Builder()
                                .setName("metric.name")
                                .setTags(ImmutableMultimap.of("tag1", "tag1", "tag2", "tag2"))
                                .setAggregators(ImmutableList.of(new Aggregator.Builder()
                                        .setName("avg")
                                        .setAlignSampling(Optional.of(Boolean.TRUE))
                                        .setSampling(Optional.of(new Sampling.Builder().setPeriod(Period.days(1)).build()))
                                        .build()
                                ))
                                .build()
                        )
                )
                .build()
        ).toCompletableFuture().get();

        JSONAssert.assertEquals(readResource("testQueryMetric.response"), OBJECT_MAPPER.writeValueAsString(response), JSONCompareMode.STRICT);
    }

    private String readResource(final String resourceSuffix) {
        try {
            return IOUtils.readUtf8(getClass().getClassLoader().getResourceAsStream("com/arpnetworking/kairos/client/" + CLASS_NAME + "." + resourceSuffix + ".json"));
        } catch (final IOException e) {
            fail("Failed with exception: " + e);
            return null;
        }
    }
}
