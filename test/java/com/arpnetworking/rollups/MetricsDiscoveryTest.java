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
package com.arpnetworking.rollups;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.TestActorRef;
import akka.testkit.javadsl.TestKit;
import com.arpnetworking.commons.akka.GuiceActorCreator;
import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.KairosMetricNamesQueryResponse;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

/**
 * Test cases for {@link MetricsDiscovery}.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public final class MetricsDiscoveryTest {

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(_config.getString(eq("rollup.fetch.interval"))).thenReturn("1h");
        when(_config.getStringList(eq("rollup.metric.whitelist"))).thenReturn(Collections.emptyList());
        when(_config.getStringList(eq("rollup.metric.blacklist"))).thenReturn(Collections.emptyList());

        _injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(KairosDbClient.class).toInstance(_kairosDbClient);
                bind(Config.class).toInstance(_config);
                bind(PeriodicMetrics.class).toInstance(_periodicMetrics);
            }
        });

        //clock = new ManualClock(t0, tickSize, ZoneId.systemDefault());

        _system = ActorSystem.create(
                "test-" + SYSTEM_NAME_NONCE.getAndIncrement(),
                ConfigFactory.parseMap(AkkaClusteringConfigFactory.generateConfiguration()));

    }

    @After
    public void tearDown() {
        TestKit.shutdownActorSystem(_system);
        _system = null;
    }

    private TestActorRef<MetricsDiscovery> createActor() {
        return TestActorRef.create(_system, GuiceActorCreator.props(_injector, MetricsDiscovery.class));
    }

    @Test
    public void testEmptyMetrics() {
        when(_kairosDbClient.queryMetricNames())
                .thenReturn(CompletableFuture
                        .completedFuture(new KairosMetricNamesQueryResponse.Builder().build()));
        final TestKit testKit = new TestKit(_system);
        final ActorRef actor = createActor();
        final ActorRef testActor = testKit.getTestActor();
        actor.tell(MetricFetch.getInstance(), testActor);
        testKit.expectMsgClass(NoMoreMetrics.class);
    }

    @Test
    public void testNoMoreMetrics() {
        when(_config.getStringList(eq("rollup.metric.whitelist")))
                .thenReturn(ImmutableList.of(
                        "^metric([\\d])*$",
                        "^cmf/.*$"));
        when(_config.getStringList(eq("rollup.metric.blacklist"))).thenReturn(ImmutableList.of("^cmf/foo/.*$"));
        when(_kairosDbClient.queryMetricNames())
                .thenReturn(CompletableFuture
                        .completedFuture(new KairosMetricNamesQueryResponse.Builder()
                                .setResults(ImmutableList.of(
                                        "metric1",
                                        "cmf/web_perf/time_to_interactive",
                                        "cmf/foo/bar",
                                        "cmf/test/foobar"
                                ))
                                .build()));

        new TestKit(_system) {{
            final TestActorRef<MetricsDiscovery> actor = createActor();
            final ActorRef testActor = getTestActor();

            awaitAssert(() -> {
                actor.tell(MetricFetch.getInstance(), testActor);
                return expectMsg("metric1");
            });

            awaitAssert(() -> {
                actor.tell(MetricFetch.getInstance(), testActor);
                return expectMsg("cmf/web_perf/time_to_interactive");
            });

            awaitAssert(() -> {
                actor.tell(MetricFetch.getInstance(), testActor);
                return expectMsg("cmf/test/foobar");
            });

            actor.tell(MetricFetch.getInstance(), testActor);
            expectMsgClass(NoMoreMetrics.class);
        }};
    }

    @Test
    public void testMetricsFiltering() {
        when(_kairosDbClient.queryMetricNames())
                .thenReturn(CompletableFuture
                        .completedFuture(new KairosMetricNamesQueryResponse.Builder()
                                .setResults(ImmutableList.of("metric1"))
                                .build()));
        new TestKit(_system) {{
            final ActorRef actor = createActor();
            final ActorRef testActor = getTestActor();
            awaitAssert(() -> {
                actor.tell(MetricFetch.getInstance(), testActor);
                return expectMsg("metric1");
            });

            actor.tell(MetricFetch.getInstance(), testActor);
            expectMsgClass(NoMoreMetrics.class);
        }};
    }

    @Test
    public void testRefresh() {
        when(_config.getString(eq("rollup.fetch.interval"))).thenReturn("3s");
        when(_kairosDbClient.queryMetricNames())
                .thenReturn(CompletableFuture
                        .completedFuture(new KairosMetricNamesQueryResponse.Builder()
                                .setResults(ImmutableList.of("metric1", "metric1_1h", "metric1_1d"))
                                .build()))
                .thenReturn(CompletableFuture
                        .completedFuture(new KairosMetricNamesQueryResponse.Builder()
                                .setResults(ImmutableList.of("metric2"))
                                .build()
                        ));
        new TestKit(_system) {{
            final ActorRef actor = createActor();
            final ActorRef testActor = getTestActor();
            awaitAssert(() -> {
                actor.tell(MetricFetch.getInstance(), testActor);
                return expectMsg("metric1");
            });

            actor.tell(MetricFetch.getInstance(), testActor);
            final NoMoreMetrics msg1 = expectMsgClass(NoMoreMetrics.class);
            assertNotNull(msg1);
            assertTrue(msg1.getNextRefreshMillis() < 3000);


            awaitAssert(Duration.ofSeconds(4), () -> {
                actor.tell(MetricFetch.getInstance(), testActor);
                return expectMsg("metric2");
            });

            actor.tell(MetricFetch.getInstance(), testActor);
            final NoMoreMetrics msg2 = expectMsgClass(NoMoreMetrics.class);
            assertNotNull(msg2);
            assertTrue(msg2.getNextRefreshMillis() < 3000);
        }};
    }

    @Test
    public void testFilterMetricNames() {
        final boolean whiteListDefault = true;
        final boolean blackListDefault = false;
        final Collection<String> names = Arrays.asList(
                "web_perf/foo",
                "desktop/bar",
                "mobile/abc");

        // No white list and no black list => everything is matched
        assertEquals(
                names,
                MetricsDiscovery.filterMetricNames(
                        names,
                        MetricsDiscovery.toPredicate(Collections.emptyList(), whiteListDefault),
                        MetricsDiscovery.toPredicate(Collections.emptyList(), blackListDefault))
                .collect(Collectors.toList()));

        // Only white list => only white listed is matched
        assertEquals(
                Collections.singletonList("web_perf/foo"),
                MetricsDiscovery.filterMetricNames(
                        names,
                        MetricsDiscovery.toPredicate(Collections.singletonList("web_perf/.*"), whiteListDefault),
                        MetricsDiscovery.toPredicate(Collections.emptyList(), blackListDefault))
                        .collect(Collectors.toList()));

        // Both white list and black list => only white listed is matched
        assertEquals(
                Collections.singletonList("web_perf/foo"),
                MetricsDiscovery.filterMetricNames(
                        names,
                        MetricsDiscovery.toPredicate(Collections.singletonList("web_perf/.*"), whiteListDefault),
                        MetricsDiscovery.toPredicate(Collections.singletonList("desktop/.*"), blackListDefault))
                        .collect(Collectors.toList()));

        // Only black list => non-black listed is matched
        assertEquals(
                Arrays.asList("web_perf/foo", "mobile/abc"),
                MetricsDiscovery.filterMetricNames(
                        names,
                        MetricsDiscovery.toPredicate(Collections.emptyList(), whiteListDefault),
                        MetricsDiscovery.toPredicate(Collections.singletonList("desktop/.*"), blackListDefault))
                        .collect(Collectors.toList()));

        // Multiple white list => white listed is matched
        assertEquals(
                Arrays.asList("web_perf/foo", "desktop/bar"),
                MetricsDiscovery.filterMetricNames(
                        names,
                        MetricsDiscovery.toPredicate(Arrays.asList("web_perf/.*", "desktop/.*"), whiteListDefault),
                        MetricsDiscovery.toPredicate(Collections.emptyList(), blackListDefault))
                        .collect(Collectors.toList()));

        // Only multiple black list => non-black listed is matched
        assertEquals(
                Arrays.asList("mobile/abc"),
                MetricsDiscovery.filterMetricNames(
                        names,
                        MetricsDiscovery.toPredicate(Collections.emptyList(), whiteListDefault),
                        MetricsDiscovery.toPredicate(Arrays.asList("web_perf/.*", "desktop/.*"), blackListDefault))
                        .collect(Collectors.toList()));

        // Black list takes precedence over white list
        assertEquals(
                Collections.singletonList("web_perf/foo"),
                MetricsDiscovery.filterMetricNames(
                        names,
                        MetricsDiscovery.toPredicate(Arrays.asList("web_perf/.*", "desktop/.*"), whiteListDefault),
                        MetricsDiscovery.toPredicate(Collections.singletonList("desktop/.*"), blackListDefault))
                        .collect(Collectors.toList()));
    }

    private Injector _injector;
    @Mock
    private KairosDbClient _kairosDbClient;
    @Mock
    private Config _config;
    @Mock
    private PeriodicMetrics _periodicMetrics;
    private ActorSystem _system;

    private static final AtomicLong SYSTEM_NAME_NONCE = new AtomicLong(0);
}
