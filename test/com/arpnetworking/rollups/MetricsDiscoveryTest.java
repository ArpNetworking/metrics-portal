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
import akka.testkit.javadsl.TestKit;
import com.arpnetworking.commons.akka.GuiceActorCreator;
import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.KairosMetricNamesQueryResponse;
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
import scala.concurrent.duration.Duration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

/**
 * Test cases for {@link MetricsDiscovery}.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public class MetricsDiscoveryTest {
    private Injector _injector;
    @Mock
    private KairosDbClient _kairosDbClient;
    @Mock
    private Config _config;
    private ActorSystem _system;

    private static final AtomicLong systemNameNonce = new AtomicLong(0);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(_config.getString(eq("rollup.fetch.interval"))).thenReturn("1h");

        _injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(KairosDbClient.class).toInstance(_kairosDbClient);
                bind(Config.class).toInstance(_config);
            }
        });

        //clock = new ManualClock(t0, tickSize, ZoneId.systemDefault());

        _system = ActorSystem.create(
                "test-"+systemNameNonce.getAndIncrement(),
                ConfigFactory.parseMap(AkkaClusteringConfigFactory.generateConfiguration()));

    }

    @After
    public void tearDown() {
        TestKit.shutdownActorSystem(_system);
        _system = null;
    }

    private ActorRef createActor() {
        return _system.actorOf(GuiceActorCreator.props(_injector, MetricsDiscovery.class));
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
        testKit.expectMsg(NoMoreMetrics.getInstance());
    }

    @Test
    public void testNoMoreMetrics() {
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
            expectMsg(NoMoreMetrics.getInstance());
        }};
    }

    @Test
    public void testRefresh() {
        when(_config.getString(eq("rollup.fetch.interval"))).thenReturn("3s");
        when(_kairosDbClient.queryMetricNames())
                .thenReturn(CompletableFuture
                        .completedFuture(new KairosMetricNamesQueryResponse.Builder()
                                .setResults(ImmutableList.of("metric1"))
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
            expectMsg(NoMoreMetrics.getInstance());

            awaitAssert(Duration.create("4s"), () -> {
                actor.tell(MetricFetch.getInstance(), testActor);
                return expectMsg("metric2");
            });

            actor.tell(MetricFetch.getInstance(), testActor);
            expectMsg(NoMoreMetrics.getInstance());
        }};
    }
}
