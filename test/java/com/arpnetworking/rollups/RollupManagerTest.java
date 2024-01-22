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
import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.arpnetworking.kairos.client.KairosDbRequestException;
import com.arpnetworking.metrics.impl.NoOpMetricsFactory;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
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
    @Mock
    private PeriodicMetrics _periodicMetrics;
    @Mock
    private Features _features;
    @Mock
    private RollupPartitioner _partitioner;
    private ActorSystem _system;

    private TestKit _consistencyChecker;

    private static final AtomicLong SYSTEM_NAME_NONCE = new AtomicLong(0);
    private AutoCloseable _mocks;

    @Before
    public void setUp() {
        _mocks = MockitoAnnotations.openMocks(this);
        when(_features.isRollupsEnabled()).thenReturn(true);
        when(_partitioner.mightSplittingFixFailure(Mockito.any())).thenReturn(false);

        _system = ActorSystem.create(
                "test-" + SYSTEM_NAME_NONCE.getAndIncrement(),
                ConfigFactory.parseMap(AkkaClusteringConfigFactory.generateConfiguration()));

        _consistencyChecker = new TestKit(_system);

    }

    @After
    public void tearDown() {
        TestKit.shutdownActorSystem(_system);
        _system = null;
        if (_mocks != null) {
            try {
                _mocks.close();
                // CHECKSTYLE.OFF: IllegalCatch - Ignore all errors when closing the mock
            } catch (final Exception ignored) { }
                // CHECKSTYLE.ON: IllegalCatch
        }
    }

    private TestActorRef<RollupManager> createActor() {
        return TestActorRef.create(_system, RollupManager.props(
                _periodicMetrics,
                new NoOpMetricsFactory(),
                _partitioner,
                _consistencyChecker.getRef(),
                1
        ));
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

    @Test
    public void testRequestsConsistencyCheck() {
        final TestActorRef<RollupManager> actor = createActor();
        actor.underlyingActor().setConsistencyCheckDelay(Duration.ZERO);

        actor.tell(
                ThreadLocalBuilder.build(RollupExecutor.FinishRollupMessage.Builder.class, b -> b
                        .setRollupDefinition(new RollupDefinition.Builder()
                            .setSourceMetricName("my_metric")
                            .setDestinationMetricName("my_metric_1h")
                            .setPeriod(RollupPeriod.HOURLY)
                            .setStartTime(Instant.EPOCH)
                            .setAllMetricTags(ImmutableMultimap.of("tag", "val1", "tag", "val2"))
                            .setFilterTags(ImmutableMap.of("tag", "val1"))
                            .build()
                )),
                ActorRef.noSender()
        );
        _consistencyChecker.expectMsg(ThreadLocalBuilder.build(ConsistencyChecker.Task.Builder.class, b -> b
                .setSourceMetricName("my_metric")
                .setRollupMetricName("my_metric_1h")
                .setPeriod(RollupPeriod.HOURLY)
                .setStartTime(Instant.EPOCH)
                .setFilterTags(ImmutableMap.of("tag", "val1"))
                .setTrigger(ConsistencyChecker.Task.Trigger.WRITE_COMPLETED)
        ));

    }
}
