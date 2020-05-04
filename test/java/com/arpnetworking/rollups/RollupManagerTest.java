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
import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.arpnetworking.kairos.client.KairosDbRequestException;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.impl.NoOpMetricsFactory;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.ConfigFactory;
import models.internal.Features;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.when;

/**
 * Test cases for {@link RollupManager}.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public final class RollupManagerTest {
    private Injector _injector;
    @Mock
    private PeriodicMetrics _periodicMetrics;
    @Mock
    private Features _features;
    @Mock
    private RollupPartitioner _partitioner;
    private ActorSystem _system;

    private static final AtomicLong SYSTEM_NAME_NONCE = new AtomicLong(0);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(_features.isRollupsEnabled()).thenReturn(true);
        when(_partitioner.mightSplittingFixFailure(Mockito.any())).thenReturn(false);

        _injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(PeriodicMetrics.class).toInstance(_periodicMetrics);
                bind(Features.class).toInstance(_features);
                bind(MetricsFactory.class).toInstance(new NoOpMetricsFactory());
            }
        });

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
        return TestActorRef.create(_system, GuiceActorCreator.props(_injector, RollupManager.class));
    }

    @Test
    public void testStoreRollupDefinition() {
        final TestKit testKit = new TestKit(_system);
        final ActorRef actor = createActor();
        final ActorRef testActor = testKit.getTestActor();
        final RollupDefinition.Builder rollupDefBuilder = new RollupDefinition.Builder()
                .setSourceMetricName("foo")
                .setDestinationMetricName("foo_1h")
                .setPeriod(RollupPeriod.HOURLY)
                .setAllMetricTags(ImmutableMultimap.of("bar", "val"))
                .setStartTime(Instant.EPOCH);
        final RollupDefinition rollupDef = rollupDefBuilder.build();
        final RollupDefinition rollupDef2 = rollupDefBuilder
                .setDestinationMetricName("foo_1d")
                .setPeriod(RollupPeriod.DAILY).build();

        actor.tell(rollupDef, testActor);
        actor.tell(rollupDef2, testActor);
        actor.tell(RollupFetch.getInstance(), testActor);
        testKit.expectMsg(rollupDef);
        actor.tell(RollupFetch.getInstance(), testActor);
        testKit.expectMsg(rollupDef2);
        actor.tell(RollupFetch.getInstance(), testActor);
        testKit.expectMsgClass(NoMoreRollups.class);
    }

    @Test
    public void testDeDupsRollups() {
        final TestKit testKit = new TestKit(_system);
        final ActorRef actor = createActor();
        final ActorRef testActor = testKit.getTestActor();
        final RollupDefinition.Builder rollupDefBuilder = new RollupDefinition.Builder()
                .setSourceMetricName("foo")
                .setDestinationMetricName("foo_1h")
                .setPeriod(RollupPeriod.HOURLY)
                .setAllMetricTags(ImmutableMultimap.of("bar", "val"))
                .setStartTime(Instant.EPOCH);
        final RollupDefinition rollupDef = rollupDefBuilder.build();
        final RollupDefinition rollupDef2 = rollupDefBuilder.build();
        actor.tell(rollupDef, testActor);
        actor.tell(rollupDef2, testActor);
        actor.tell(RollupFetch.getInstance(), testActor);
        testKit.expectMsg(rollupDef);
        actor.tell(RollupFetch.getInstance(), testActor);
        testKit.expectMsgClass(NoMoreRollups.class);
    }

    @Test
    public void testReturnsRollupsInChronologicalOrder() {
        final TestKit testKit = new TestKit(_system);
        final ActorRef actor = createActor();
        final ActorRef testActor = testKit.getTestActor();
        final RollupDefinition.Builder rollupDefBuilder = new RollupDefinition.Builder()
                .setSourceMetricName("foo")
                .setDestinationMetricName("foo_1h")
                .setPeriod(RollupPeriod.HOURLY)
                .setAllMetricTags(ImmutableMultimap.of("bar", "val"))
                .setStartTime(Instant.EPOCH);
        final RollupDefinition rollupDef = rollupDefBuilder.build();
        final RollupDefinition rollupDef2 = rollupDefBuilder.setStartTime(Instant.EPOCH.plus(1, ChronoUnit.HOURS)).build();
        actor.tell(rollupDef2, testActor);
        actor.tell(rollupDef, testActor);
        actor.tell(RollupFetch.getInstance(), testActor);
        testKit.expectMsg(rollupDef);
        actor.tell(RollupFetch.getInstance(), testActor);
        testKit.expectMsg(rollupDef2);
        actor.tell(RollupFetch.getInstance(), testActor);
        testKit.expectMsgClass(NoMoreRollups.class);
    }

    @Test
    public void testSplitsFailedRollups() throws Exception {
        when(_partitioner.mightSplittingFixFailure(Mockito.any())).thenReturn(true);

        final TestKit testKit = new TestKit(_system);
        final ActorRef actor = createActor();
        final ActorRef testActor = testKit.getTestActor();
        final RollupDefinition rollupDef = new RollupDefinition.Builder()
                .setSourceMetricName("foo")
                .setDestinationMetricName("foo_1h")
                .setPeriod(RollupPeriod.HOURLY)
                .setAllMetricTags(ImmutableMultimap.of("tag", "val1", "tag", "val2"))
                .setStartTime(Instant.EPOCH)
                .build();

        final ImmutableSet<RollupDefinition> children = ImmutableSet.of("val1", "val2").stream()
            .map(tag -> RollupDefinition.Builder.<RollupDefinition, RollupDefinition.Builder>clone(rollupDef)
                    .setFilterTags(ImmutableMap.of("tag", tag))
                    .build())
            .collect(ImmutableSet.toImmutableSet());
        when(_partitioner.splitJob(rollupDef)).thenReturn(children);

        actor.tell(
                ThreadLocalBuilder.build(RollupExecutor.FinishRollupMessage.Builder.class, b -> b
                        .setRollupDefinition(rollupDef)
                        .setFailure(new KairosDbRequestException(502, "", URI.create("http://kairosdb"), Duration.ofMinutes(5)))
                ),
                testActor
        );

        actor.tell(RollupFetch.getInstance(), testActor);
        actor.tell(RollupFetch.getInstance(), testActor);
        testKit.expectMsgAllOf(children.toArray());

        actor.tell(RollupFetch.getInstance(), testActor);
        testKit.expectMsg(NoMoreRollups.getInstance());
    }
}
