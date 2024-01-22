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
import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.Aggregator;
import com.arpnetworking.kairos.client.models.DataPoint;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.Sampling;
import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.typesafe.config.Config;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Named;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

/**
 * Test cases for the {@link RollupExecutor} actor.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public class RollupExecutorTest {
    private Injector _injector;
    @Mock
    private KairosDbClient _kairosDbClient;
    @Mock
    private Config _config;
    @Mock
    private PeriodicMetrics _periodicMetrics;

    private TestKit _probe;

    private ActorSystem _system;
    private AutoCloseable _mocks;

    @Before
    public void setUp() {
        _mocks = MockitoAnnotations.openMocks(this);
        when(_config.getString(eq("rollup.executor.pollInterval"))).thenReturn("3sec");
        when(_config.getString(eq("rollup.ttl"))).thenReturn("0sec");

        _system = ActorSystem.create();

        _probe = new TestKit(_system);

        _injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(KairosDbClient.class).toInstance(_kairosDbClient);
                bind(Config.class).toInstance(_config);
                bind(ActorRef.class)
                        .annotatedWith(Names.named("RollupManager"))
                        .toInstance(_probe.getRef());
                bind(PeriodicMetrics.class).toInstance(_periodicMetrics);
            }
        });
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

    private ActorRef createActor() {
        return _system.actorOf(GuiceActorCreator.props(_injector, TestRollupExecutor.class));
    }

    @Test
    public void testSendsFetchOnStartup() {
        final ActorRef actor = createActor();
        _probe.expectMsg(RollupExecutor.FETCH_ROLLUP);
        actor.tell(RollupExecutor.FETCH_ROLLUP, ActorRef.noSender());
        _probe.expectMsg(RollupFetch.getInstance());
    }

    @Test
    public void testFetchesNextRollup() {
        final ActorRef actor = createActor();
        _probe.expectMsg(RollupExecutor.FETCH_ROLLUP);
        final RollupExecutor.FinishRollupMessage finished = ThreadLocalBuilder.build(
                RollupExecutor.FinishRollupMessage.Builder.class,
                b -> b.setRollupDefinition(new RollupDefinition.Builder()
                        .setSourceMetricName("metric")
                        .setDestinationMetricName("metric_1h")
                        .setPeriod(RollupPeriod.HOURLY)
                        .setStartTime(Instant.EPOCH)
                        .setAllMetricTags(ImmutableMultimap.of())
                        .build()
                )
        );
        actor.tell(finished, ActorRef.noSender());
        _probe.expectMsg(finished);
        _probe.expectMsg(RollupFetch.getInstance());
    }

    @Test
    public void testPerformsRollup() {
        when(_kairosDbClient.queryMetrics(any())).thenAnswer(invocation -> {
            final CompletableFuture<MetricsQueryResponse> future = new CompletableFuture<>();
            final Object arg0 = invocation.getArguments()[0];
            if (arg0 instanceof MetricsQuery) {
                final MetricsQuery query = (MetricsQuery) arg0;
                final String metricName = query.getMetrics().get(0).getName();
                final MetricsQueryResponse.QueryResult.Builder builder = new MetricsQueryResponse.QueryResult.Builder();
                builder.setName(metricName);

                builder.setValues(ImmutableList.of(new DataPoint.Builder()
                        .setTime(Instant.EPOCH)
                        .setValue(0.0)
                        .build()
                ));
                future.complete(new MetricsQueryResponse.Builder()
                        .setQueries(ImmutableList.of(new MetricsQueryResponse.Query.Builder()
                                .setResults(ImmutableList.of(builder.build()))
                                .build()))
                        .build()
                );
            }

            return future;
        });

        final ArgumentCaptor<MetricsQuery> captor = ArgumentCaptor.forClass(MetricsQuery.class);
        final ActorRef actor = createActor();
        _probe.expectMsg(RollupExecutor.FETCH_ROLLUP);

        actor.tell(
                new RollupDefinition.Builder()
                        .setSourceMetricName("metric")
                        .setDestinationMetricName("metric_1h")
                        .setPeriod(RollupPeriod.HOURLY)
                        .setAllMetricTags(ImmutableMultimap.of("tag1", "val1", "tag2", "val2"))
                        .setStartTime(Instant.EPOCH)
                        .build(),
                ActorRef.noSender());

        final RollupExecutor.FinishRollupMessage finishRollupMessage =
                _probe.expectMsgClass(RollupExecutor.FinishRollupMessage.class);
        assertFalse(finishRollupMessage.isFailure());
        assertEquals("metric", finishRollupMessage.getRollupDefinition().getSourceMetricName());
        assertEquals(RollupPeriod.HOURLY, finishRollupMessage.getRollupDefinition().getPeriod());

        verify(_kairosDbClient, times(1)).queryMetrics(captor.capture());
        final MetricsQuery rollupQuery = captor.getValue();
        assertEquals("metric", rollupQuery.getMetrics().get(0).getName());
        assertEquals(Optional.of(Instant.EPOCH), rollupQuery.getStartTime());
        assertTrue(rollupQuery.getEndTime().isPresent());
        assertEquals(Instant.EPOCH.plus(1, ChronoUnit.HOURS).minusMillis(1), rollupQuery.getEndTime().get());
        assertEquals(1, rollupQuery.getMetrics().size());
        final Metric metric = rollupQuery.getMetrics().get(0);
        assertEquals(1, metric.getGroupBy().size());
        MatcherAssert.assertThat(metric.getGroupBy().get(0), Matchers.instanceOf(MetricsQuery.QueryTagGroupBy.class));
        assertEquals(ImmutableSet.of("tag1", "tag2"), ((MetricsQuery.QueryTagGroupBy) metric.getGroupBy().get(0)).getTags());
        assertEquals(3, metric.getAggregators().size());
        assertEquals("merge", metric.getAggregators().get(0).getName());
        assertTrue(metric.getAggregators().get(0).getAlignSampling().isPresent());
        assertTrue(metric.getAggregators().get(0).getAlignSampling().get());
        assertTrue(metric.getAggregators().get(0).getAlignStartTime().isPresent());
        assertTrue(metric.getAggregators().get(0).getAlignStartTime().get());
        assertFalse(metric.getAggregators().get(0).getAlignEndTime().isPresent());
        assertTrue(metric.getAggregators().get(0).getSampling().isPresent());
        assertEquals(1L, metric.getAggregators().get(0).getSampling().get().getValue());
        assertEquals(SamplingUnit.HOURS, metric.getAggregators().get(0).getSampling().get().getUnit());
        assertEquals("save_as", metric.getAggregators().get(1).getName());
        assertEquals("metric_1h", metric.getAggregators().get(1).getOtherArgs().get("metric_name"));
        assertFalse(metric.getAggregators().get(1).getSampling().isPresent());
        assertEquals("count", metric.getAggregators().get(2).getName());
        assertFalse(metric.getAggregators().get(2).getSampling().isPresent());

        _probe.expectNoMessage();
    }

    @Test
    public void testBuildRollupQuery() {
        RollupDefinition definition = new RollupDefinition.Builder()
                .setSourceMetricName("my_metric")
                .setDestinationMetricName("my_metric_1h")
                .setAllMetricTags(ImmutableMultimap.of("tag1", "val1", "tag2", "val2"))
                .setPeriod(RollupPeriod.HOURLY)
                .setStartTime(Instant.EPOCH)
                .build();
        long ttl = 0;
        assertEquals(
                new MetricsQuery.Builder()
                        .setMetrics(ImmutableList.of(new Metric.Builder()
                                .setName("my_metric")
                                .setGroupBy(ImmutableList.of(
                                        new MetricsQuery.QueryTagGroupBy.Builder().setTags(ImmutableSet.of("tag1", "tag2")).build()))
                                .setAggregators(ImmutableList.of(
                                        new Aggregator.Builder()
                                            .setName("merge")
                                            .setAlignStartTime(true)
                                            .setAlignSampling(true)
                                            .setSampling(new Sampling.Builder().setValue(1).setUnit(SamplingUnit.HOURS).build())
                                            .build(),
                                        new Aggregator.Builder()
                                            .setName("save_as")
                                                .setOtherArgs(ImmutableMap.of(
                                                        "metric_name", "my_metric_1h",
                                                        "add_saved_from", false,
                                                        "ttl", ttl
                                                ))
                                            .build(),
                                        new Aggregator.Builder()
                                            .setName("count")
                                            .build()))
                                .build()))
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.EPOCH.plus(Duration.ofHours(1)).minusMillis(1))
                        .build(),
                RollupExecutor.buildQueryRollup(definition, ttl)
        );

        definition = new RollupDefinition.Builder()
                .setSourceMetricName("my_metric_1h")
                .setDestinationMetricName("my_metric_1d")
                .setAllMetricTags(ImmutableMultimap.of("tag1", "val1", "tag2", "val2"))
                .setPeriod(RollupPeriod.DAILY)
                .setStartTime(Instant.EPOCH)
                .build();
        ttl = 1234;
        assertEquals(
                new MetricsQuery.Builder()
                        .setMetrics(ImmutableList.of(new Metric.Builder()
                                .setName("my_metric_1h")
                                .setGroupBy(ImmutableList.of(
                                        new MetricsQuery.QueryTagGroupBy.Builder().setTags(ImmutableSet.of("tag1", "tag2")).build()))
                                .setAggregators(ImmutableList.of(
                                        new Aggregator.Builder()
                                                .setName("merge")
                                                .setAlignStartTime(true)
                                                .setAlignSampling(true)
                                                .setSampling(new Sampling.Builder().setValue(1).setUnit(SamplingUnit.DAYS).build())
                                                .build(),
                                        new Aggregator.Builder()
                                                .setName("save_as")
                                                .setOtherArgs(ImmutableMap.of(
                                                        "metric_name", "my_metric_1d",
                                                        "add_saved_from", false,
                                                        "ttl", ttl
                                                ))
                                                .build(),
                                        new Aggregator.Builder()
                                                .setName("count")
                                                .build()))
                                .build()))
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.EPOCH.plus(Duration.ofDays(1)).minusMillis(1))
                        .build(),
                RollupExecutor.buildQueryRollup(definition, ttl)
        );
    }

    private static final Supplier<RollupExecutor.FinishRollupMessage.Builder> FULLY_SPECIFIED_FINISH_MESSAGE_BUILDER = () ->
            new RollupExecutor.FinishRollupMessage.Builder()
                    .setRollupDefinition(TestBeanFactory.createRollupDefinitionBuilder().build())
                    .setFailure(new RuntimeException());

    @Test
    public void testFinishMessageBuilderReset() throws Exception {
        com.arpnetworking.commons.test.ThreadLocalBuildableTestHelper.testReset(FULLY_SPECIFIED_FINISH_MESSAGE_BUILDER.get());
    }

    /**
     * Test actor class that overrides {@code getSelf()} so that messages passed back to the actor
     * can be intercepted.
     */
    public static final class TestRollupExecutor extends RollupExecutor {
        @Inject
        public TestRollupExecutor(
                final Config configuration,
                @Named("RollupManager") final ActorRef testActor,
                final KairosDbClient kairosDbClient,
                final PeriodicMetrics metrics) {
            super(configuration, testActor, kairosDbClient, metrics);
            _self = testActor;
        }

        @Override
        public ActorRef getSelf() {
            return _self;
        }

        private final ActorRef _self;
    }
}
