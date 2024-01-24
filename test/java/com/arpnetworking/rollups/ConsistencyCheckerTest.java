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
package com.arpnetworking.rollups;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.testkit.TestActorRef;
import org.apache.pekko.testkit.javadsl.TestKit;
import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.utility.test.ResourceHelper;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.ConfigFactory;
import net.sf.oval.exception.ConstraintsViolatedException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test cases for {@link ConsistencyChecker}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class ConsistencyCheckerTest {
    @Mock
    private KairosDbClient _kairosDbClient;
    @Mock
    private MetricsFactory _metricsFactory;
    @Mock
    private PeriodicMetrics _periodicMetrics;
    private ActorSystem _system;

    private static final AtomicLong SYSTEM_NAME_NONCE = new AtomicLong(0);
    private AutoCloseable _mocks;

    @Before
    public void setUp() throws Exception {
        _mocks = MockitoAnnotations.openMocks(this);

        _system = ActorSystem.create();

        _system = ActorSystem.create(
                "test-" + SYSTEM_NAME_NONCE.getAndIncrement(),
                ConfigFactory.parseMap(AkkaClusteringConfigFactory.generateConfiguration())
        );

        when(_kairosDbClient.queryMetrics(
                ResourceHelper.loadResourceAs(getClass(), "my_metric.hourly.t0.human_requested.request", MetricsQuery.class))
        ).thenReturn(CompletableFuture.completedFuture(
                ResourceHelper.loadResourceAs(getClass(), "my_metric.hourly.t0.human_requested.response", MetricsQueryResponse.class)
        ));

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

    private TestActorRef<ConsistencyChecker> createActor(
            final int maxConcurrentRequests,
            final int bufferSize
    ) {
        return TestActorRef.create(
                _system,
                ConsistencyChecker.props(_kairosDbClient,
                _metricsFactory,
                _periodicMetrics,
                maxConcurrentRequests,
                bufferSize
        ));
    }

    @Test
    public void testTaskSubmissionResponses() throws Exception {
        final ActorRef checker = createActor(0, 1);

        final ConsistencyChecker.Task task = TestBeanFactory.buildConsistencyCheckerTaskBuilder(b -> b
                .setSourceMetricName("foo")
        );
        Patterns.ask(checker, task, Duration.ofSeconds(10))
                .thenAccept(response -> assertEquals(task, response))
                .toCompletableFuture()
                .get();


        final ConsistencyChecker.Task otherTask = TestBeanFactory.buildConsistencyCheckerTaskBuilder(b -> b
                .setSourceMetricName("other")
        );
        Patterns.ask(checker, otherTask, Duration.ofSeconds(10))
                .handle((response, error) -> {
                    assertTrue(error instanceof ConsistencyChecker.BufferFull);
                    return null;
                })
                .toCompletableFuture()
                .get();
    }

    @Test
    public void testKairosDbInteraction() throws Exception {
        final ActorRef actor = createActor(1, 1);

        actor.tell(
                ThreadLocalBuilder.build(ConsistencyChecker.Task.Builder.class, b -> b
                        .setSourceMetricName("my_metric")
                        .setRollupMetricName("my_metric_1h")
                        .setPeriod(RollupPeriod.HOURLY)
                        .setStartTime(Instant.EPOCH)
                        .setTrigger(ConsistencyChecker.Task.Trigger.ON_DEMAND)
                ),
                ActorRef.noSender()
        );
        actor.tell(ConsistencyChecker.TICK, ActorRef.noSender());

        final ArgumentCaptor<MetricsQuery> captor = ArgumentCaptor.forClass(MetricsQuery.class);
        verify(_kairosDbClient).queryMetrics(captor.capture());
        assertEquals(
            ResourceHelper.loadResourceAs(getClass(), "my_metric.hourly.t0.human_requested.request", MetricsQuery.class),
            captor.getValue()
        );
    }

    @Test
    public void testParseSampleCounts() throws Exception {
        final ConsistencyChecker.Task task = ThreadLocalBuilder.build(ConsistencyChecker.Task.Builder.class, b -> b
                .setSourceMetricName("my_metric")
                .setRollupMetricName("my_metric_1h")
                .setPeriod(RollupPeriod.HOURLY)
                .setStartTime(Instant.EPOCH)
                .setTrigger(ConsistencyChecker.Task.Trigger.ON_DEMAND)
        );
        ConsistencyChecker.SampleCounts actual = ConsistencyChecker.parseSampleCounts(
                task,
                ResourceHelper.loadResourceAs(getClass(), "my_metric.hourly.t0.human_requested.response", MetricsQueryResponse.class)
        );
        assertEquals(
                ThreadLocalBuilder.build(ConsistencyChecker.SampleCounts.Builder.class, b -> b
                        .setTask(task)
                        .setSourceSampleCount(100)
                        .setRollupSampleCount(80)
                ),
                actual
        );

        actual = ConsistencyChecker.parseSampleCounts(
                task,
                ResourceHelper.loadResourceAs(
                        getClass(),
                        "my_metric.hourly.t0.human_requested.no-data.response",
                        MetricsQueryResponse.class
                )
        );
        assertEquals(
                new ConsistencyChecker.SampleCounts.Builder()
                        .setTask(task)
                        .setSourceSampleCount(100)
                        .setRollupSampleCount(0)
                        .build(),
                actual
        );
    }

    @Test
    public void testTaskValidation() {
        final ConsistencyChecker.Task.Builder builder = new ConsistencyChecker.Task.Builder()
                .setSourceMetricName("my_metric")
                .setRollupMetricName("my_metric_1h")
                .setPeriod(RollupPeriod.HOURLY)
                .setTrigger(ConsistencyChecker.Task.Trigger.ON_DEMAND);

        builder.setStartTime(Instant.EPOCH).build();
        builder.setStartTime(Instant.EPOCH.plus(Duration.ofHours(1))).build();

        try {
            builder.setStartTime(Instant.EPOCH.plus(Duration.ofSeconds(1))).build();
            fail("expected ConstraintsViolatedException");
        } catch (final ConstraintsViolatedException e) {
        }
    }

    private static final Supplier<ConsistencyChecker.Task.Builder> FULLY_SPECIFIED_TASK_BUILDER = () ->
            new ConsistencyChecker.Task.Builder()
                    .setSourceMetricName("foo")
                    .setRollupMetricName("bar")
                    .setPeriod(RollupPeriod.HOURLY)
                    .setStartTime(Instant.EPOCH)
                    .setFilterTags(ImmutableMap.of("k", "v"))
                    .setTrigger(ConsistencyChecker.Task.Trigger.WRITE_COMPLETED);

    private static final Supplier<ConsistencyChecker.SampleCounts.Builder> FULLY_SPECIFIED_SAMPLE_COUNTS_BUILDER = () ->
            new ConsistencyChecker.SampleCounts.Builder()
                    .setSourceSampleCount(1)
                    .setRollupSampleCount(1)
                    .setTask(FULLY_SPECIFIED_TASK_BUILDER.get().build())
                    .setFailure(new RuntimeException());

    @Test
    public void testTaskBuilderReset() throws Exception {
        com.arpnetworking.commons.test.ThreadLocalBuildableTestHelper.testReset(FULLY_SPECIFIED_TASK_BUILDER.get());
    }

    @Test
    public void testSampleCountsBuilderReset() throws Exception {
        com.arpnetworking.commons.test.ThreadLocalBuildableTestHelper.testReset(FULLY_SPECIFIED_SAMPLE_COUNTS_BUILDER.get());
    }
}
