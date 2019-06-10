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
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Named;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

/**
 * Test cases for the RollupGenerator actor.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public class RollupGeneratorTest {
    private Injector _injector;
    @Mock
    private KairosDbClient _kairosDbClient;
    @Mock
    private Config _config;
    @Mock
    private PeriodicMetrics _periodicMetrics;
    private TestKit _probe;

    private Clock _clock;
    private ActorSystem _system;

    private static final AtomicLong SYSTEM_NAME_NONCE = new AtomicLong(0);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(_config.getInt(eq("rollup.maxBackFill.periods"))).thenReturn(4);
        when(_config.getString(eq("rollup.fetch.backoff"))).thenReturn("5min");


        _system = ActorSystem.create(
                "test-" + SYSTEM_NAME_NONCE.getAndIncrement(),
                ConfigFactory.parseMap(AkkaClusteringConfigFactory.generateConfiguration()));

        _probe = new TestKit(_system);

        _clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"));

        _injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(KairosDbClient.class).toInstance(_kairosDbClient);
                bind(Config.class).toInstance(_config);
                bind(ActorRef.class)
                        .annotatedWith(Names.named("RollupsMetricsDiscovery"))
                        .toInstance(_probe.getRef());
                bind(Clock.class).toInstance(_clock);
                bind(PeriodicMetrics.class).toInstance(_periodicMetrics);
            }
        });

    }

    @After
    public void tearDown() {
        TestKit.shutdownActorSystem(_system);
        _system = null;
    }

    private ActorRef createActor() {
        return _system.actorOf(GuiceActorCreator.props(_injector, TestRollupGenerator.class));
    }

    @Test
    public void testSendsFetchOnStartup() {
        createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);
    }

    @Test
    public void testFetchesTagNames() {
        when(_kairosDbClient.queryMetricTags(any())).thenReturn(
                CompletableFuture.completedFuture(new MetricsQueryResponse.Builder()
                        .setQueries(ImmutableList.of(
                                new MetricsQueryResponse.Query.Builder()
                                        .setResults(ImmutableList.of(new MetricsQueryResponse.QueryResult.Builder()
                                                .setName("metric")
                                                .setTags(ImmutableMultimap.of("tag1", "value1", "tag2", "value2"))
                                                .build()))
                                        .build()))
                        .build()));

        final ArgumentCaptor<MetricsQuery> captor = ArgumentCaptor.forClass(MetricsQuery.class);
        final ActorRef actor = createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        actor.tell("metric", ActorRef.noSender());
        final TagNamesMessage tagNamesMessage = _probe.expectMsgClass(TagNamesMessage.class);
        assertFalse(tagNamesMessage.isFailure());
        assertEquals("metric", tagNamesMessage.getMetricName());
        assertEquals(2, tagNamesMessage.getTagNames().size());

        verify(_kairosDbClient, times(1)).queryMetricTags(captor.capture());
        final MetricsQuery tagQuery = captor.getValue();
        assertEquals("metric", tagQuery.getMetrics().get(0).getName());
        assertEquals(Instant.ofEpochSecond(0), tagQuery.getStartTime());
    }

    @Test
    public void testHandlesTagNamesFailure() {
        final CompletableFuture<MetricsQueryResponse> future =  new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Failure"));
        when(_kairosDbClient.queryMetricTags(any())).thenReturn(future);

        final ActorRef actor = createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        actor.tell("metric", ActorRef.noSender());
        final TagNamesMessage tagNamesMessage = _probe.expectMsgClass(TagNamesMessage.class);
        assertTrue(tagNamesMessage.isFailure());
        assertNotNull(tagNamesMessage.getFailure());
        assertEquals("Failure", tagNamesMessage.getFailure().orElse(null).getMessage());
    }

    @Test
    public void testFetchesLastDataPoints() {
        when(_kairosDbClient.queryMetrics(any())).thenAnswer(invocation -> {
            final Object arg0 = invocation.getArguments()[0];
            if (arg0 instanceof MetricsQuery) {
                final MetricsQuery query = (MetricsQuery) arg0;
                final String metricName = query.getMetrics().get(0).getName();
                final MetricsQueryResponse.QueryResult.Builder builder = new MetricsQueryResponse.QueryResult.Builder();
                builder.setName(metricName);

                if (metricName.equals("metric_1h")) {
                    builder.setValues(ImmutableList.of(new MetricsQueryResponse.DataPoint.Builder()
                            .setTime(RollupPeriod.HOURLY.recentEndTime(_clock.instant()))
                            .setValue(0.0)
                            .build()
                    ));
                } else if (metricName.equals("metric_1d")) {
                    builder.setValues(ImmutableList.of(new MetricsQueryResponse.DataPoint.Builder()
                            .setTime(RollupPeriod.DAILY.recentEndTime(_clock.instant()))
                            .setValue(0.0)
                            .build()
                    ));
                }
                return CompletableFuture.completedFuture(new MetricsQueryResponse.Builder()
                        .setQueries(ImmutableList.of(new MetricsQueryResponse.Query.Builder()
                                .setResults(ImmutableList.of(builder.build()))
                                .build()))
                        .build()
                );
            }
            return null;
        });

        final ActorRef actor = createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        actor.tell(
                new TagNamesMessage.Builder()
                        .setMetricName("metric")
                        .setTagNames(ImmutableSet.of("tag1", "tag2"))
                        .build(),
                ActorRef.noSender());

        final LastDataPointMessage lastDataPointMessage1 = _probe.expectMsgClass(LastDataPointMessage.class);
        assertFalse(lastDataPointMessage1.isFailure());
        assertEquals("metric", lastDataPointMessage1.getMetricName());
        assertEquals(RollupPeriod.HOURLY, lastDataPointMessage1.getPeriod());
        assertTrue(lastDataPointMessage1.getLastDataPointTime().isPresent());
        assertEquals(RollupPeriod.HOURLY.recentEndTime(_clock.instant()),
                lastDataPointMessage1.getLastDataPointTime().get());
        assertEquals(2, lastDataPointMessage1.getTags().size());

        final LastDataPointMessage lastDataPointMessage2 = _probe.expectMsgClass(LastDataPointMessage.class);
        assertFalse(lastDataPointMessage2.isFailure());
        assertEquals("metric", lastDataPointMessage2.getMetricName());
        assertEquals(RollupPeriod.DAILY, lastDataPointMessage2.getPeriod());
        assertTrue(lastDataPointMessage2.getLastDataPointTime().isPresent());
        assertEquals(RollupPeriod.DAILY.recentEndTime(_clock.instant()),
                lastDataPointMessage2.getLastDataPointTime().get());

        _probe.expectNoMessage();

        actor.tell(new FinishRollupMessage.Builder().setMetricName("metric").setPeriod(RollupPeriod.HOURLY).build(), ActorRef.noSender());
        _probe.expectNoMessage();

        actor.tell(new FinishRollupMessage.Builder().setMetricName("metric").setPeriod(RollupPeriod.DAILY).build(), ActorRef.noSender());
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);
    }

    @Test
    public void testLastDataPointsSingleFailure() {
        when(_kairosDbClient.queryMetrics(any())).thenAnswer(invocation -> {
            final CompletableFuture<MetricsQueryResponse> future = new CompletableFuture<>();
            final Object arg0 = invocation.getArguments()[0];
            if (arg0 instanceof MetricsQuery) {
                final MetricsQuery query = (MetricsQuery) arg0;
                final String metricName = query.getMetrics().get(0).getName();
                final MetricsQueryResponse.QueryResult.Builder builder = new MetricsQueryResponse.QueryResult.Builder();
                builder.setName(metricName);

                if (metricName.equals("metric_1h")) {
                    future.completeExceptionally(new RuntimeException("Failure"));
                } else if (metricName.equals("metric_1d")) {
                    builder.setValues(ImmutableList.of(new MetricsQueryResponse.DataPoint.Builder()
                            .setTime(RollupPeriod.DAILY.recentEndTime(_clock.instant()))
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
            }
            return null;
        });

        final ArgumentCaptor<MetricsQuery> captor = ArgumentCaptor.forClass(MetricsQuery.class);
        final ActorRef actor = createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        actor.tell(
                new TagNamesMessage.Builder()
                        .setMetricName("metric")
                        .setTagNames(ImmutableSet.of("tag1", "tag2"))
                        .build(),
                ActorRef.noSender());

        _probe.awaitAssert(() ->
                verify(_kairosDbClient, times(2)).queryMetrics(captor.capture()));
        final MetricsQuery hourlyQuery = captor.getAllValues().get(0);
        assertEquals("metric_1h", hourlyQuery.getMetrics().get(0).getName());
        assertEquals(RollupPeriod.HOURLY.recentEndTime(_clock.instant()).minus(RollupPeriod.HOURLY.periodCountToDuration(4)),
                hourlyQuery.getStartTime());
        assertEquals(RollupPeriod.HOURLY.recentEndTime(_clock.instant()), hourlyQuery.getEndTime().get());

        final LastDataPointMessage lastDataPointMessage1 = _probe.expectMsgClass(LastDataPointMessage.class);
        assertTrue(lastDataPointMessage1.isFailure());
        assertEquals("metric", lastDataPointMessage1.getMetricName());
        assertEquals(RollupPeriod.HOURLY, lastDataPointMessage1.getPeriod());
        assertEquals("Failure", lastDataPointMessage1.getFailure().orElse(null).getMessage());

        final LastDataPointMessage lastDataPointMessage2 = _probe.expectMsgClass(LastDataPointMessage.class);
        assertFalse(lastDataPointMessage2.isFailure());
        assertEquals("metric", lastDataPointMessage2.getMetricName());
        assertEquals(RollupPeriod.DAILY, lastDataPointMessage2.getPeriod());
        assertTrue(lastDataPointMessage2.getLastDataPointTime().isPresent());
        assertEquals(RollupPeriod.DAILY.recentEndTime(_clock.instant()),
                lastDataPointMessage2.getLastDataPointTime().get());

        _probe.expectNoMessage();
    }

    @Test
    public void testPerformRollupWithNoLastDataPoint() {
        when(_kairosDbClient.queryMetrics(any())).thenAnswer(invocation -> {
            final CompletableFuture<MetricsQueryResponse> future = new CompletableFuture<>();
            final Object arg0 = invocation.getArguments()[0];
            if (arg0 instanceof MetricsQuery) {
                final MetricsQuery query = (MetricsQuery) arg0;
                final String metricName = query.getMetrics().get(0).getName();
                final MetricsQueryResponse.QueryResult.Builder builder = new MetricsQueryResponse.QueryResult.Builder();
                builder.setName(metricName);

                builder.setValues(ImmutableList.of(new MetricsQueryResponse.DataPoint.Builder()
                        .setTime(RollupPeriod.HOURLY.recentEndTime(_clock.instant()))
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
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        actor.tell(
                new LastDataPointMessage.Builder()
                        .setMetricName("metric")
                        .setPeriod(RollupPeriod.HOURLY)
                        .setTags(ImmutableSet.of("tag1", "tag2"))
                        .setLastDataPointTime(null)
                        .build(),
                ActorRef.noSender());

        final FinishRollupMessage finishRollupMessage = _probe.expectMsgClass(FinishRollupMessage.class);
        assertFalse(finishRollupMessage.isFailure());
        assertEquals("metric", finishRollupMessage.getMetricName());
        assertEquals(RollupPeriod.HOURLY, finishRollupMessage.getPeriod());

        verify(_kairosDbClient, times(1)).queryMetrics(captor.capture());
        final MetricsQuery rollupQuery = captor.getAllValues().get(0);
        assertEquals("metric", rollupQuery.getMetrics().get(0).getName());
        assertEquals(RollupPeriod.HOURLY.recentEndTime(_clock.instant()).minus(RollupPeriod.HOURLY.periodCountToDuration(4)),
                rollupQuery.getStartTime());
        assertTrue(rollupQuery.getEndTime().isPresent());
        assertEquals(RollupPeriod.HOURLY.recentEndTime(_clock.instant()).minusMillis(1), rollupQuery.getEndTime().get());
        assertEquals(1, rollupQuery.getMetrics().size());
        final Metric metric = rollupQuery.getMetrics().get(0);
        assertEquals(1, metric.getGroupBy().size());
        assertEquals("tag", metric.getGroupBy().get(0).getName());
        assertEquals(ImmutableSet.of("tag1", "tag2"), metric.getGroupBy().get(0).getOtherArgs().get("tags"));
        assertEquals(2, metric.getAggregators().size());
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

        _probe.expectNoMessage();
    }

    @Test
    public void testPerformRollupWithOldLastDataPoint() {
        when(_kairosDbClient.queryMetrics(any())).thenAnswer(invocation -> {
            final CompletableFuture<MetricsQueryResponse> future = new CompletableFuture<>();
            final Object arg0 = invocation.getArguments()[0];
            if (arg0 instanceof MetricsQuery) {
                final MetricsQuery query = (MetricsQuery) arg0;
                final String metricName = query.getMetrics().get(0).getName();
                final MetricsQueryResponse.QueryResult.Builder builder = new MetricsQueryResponse.QueryResult.Builder();
                builder.setName(metricName);

                builder.setValues(ImmutableList.of(new MetricsQueryResponse.DataPoint.Builder()
                        .setTime(RollupPeriod.HOURLY.recentEndTime(_clock.instant()))
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
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        actor.tell(
                new LastDataPointMessage.Builder()
                        .setMetricName("metric")
                        .setPeriod(RollupPeriod.HOURLY)
                        .setTags(ImmutableSet.of("tag1", "tag2"))
                        .setLastDataPointTime(Instant.EPOCH)
                        .build(),
                ActorRef.noSender());

        final FinishRollupMessage finishRollupMessage = _probe.expectMsgClass(FinishRollupMessage.class);
        assertFalse(finishRollupMessage.isFailure());
        assertEquals("metric", finishRollupMessage.getMetricName());
        assertEquals(RollupPeriod.HOURLY, finishRollupMessage.getPeriod());

        verify(_kairosDbClient, times(1)).queryMetrics(captor.capture());
        final MetricsQuery rollupQuery = captor.getValue();
        assertEquals("metric", rollupQuery.getMetrics().get(0).getName());
        assertEquals(RollupPeriod.HOURLY.recentEndTime(_clock.instant()).minus(RollupPeriod.HOURLY.periodCountToDuration(4)),
                rollupQuery.getStartTime());
        assertTrue(rollupQuery.getEndTime().isPresent());
        assertEquals(RollupPeriod.HOURLY.recentEndTime(_clock.instant()).minusMillis(1), rollupQuery.getEndTime().get());

        _probe.expectNoMessage();
    }

    @Test
    public void testPerformRollupWithRecentLastDataPoint() {
        when(_kairosDbClient.queryMetrics(any())).thenAnswer(invocation -> {
            final CompletableFuture<MetricsQueryResponse> future = new CompletableFuture<>();
            final Object arg0 = invocation.getArguments()[0];
            if (arg0 instanceof MetricsQuery) {
                final MetricsQuery query = (MetricsQuery) arg0;
                final String metricName = query.getMetrics().get(0).getName();
                final MetricsQueryResponse.QueryResult.Builder builder = new MetricsQueryResponse.QueryResult.Builder();
                builder.setName(metricName);

                builder.setValues(ImmutableList.of(new MetricsQueryResponse.DataPoint.Builder()
                        .setTime(RollupPeriod.HOURLY.recentEndTime(_clock.instant()))
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
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        final Instant lastDataPoint = RollupPeriod.HOURLY
                .recentEndTime(_clock.instant())
                .minus(RollupPeriod.HOURLY.periodCountToDuration(2));

        actor.tell(
                new LastDataPointMessage.Builder()
                        .setMetricName("metric")
                        .setPeriod(RollupPeriod.HOURLY)
                        .setTags(ImmutableSet.of("tag1", "tag2"))
                        .setLastDataPointTime(lastDataPoint)
                        .build(),
                ActorRef.noSender());

        final FinishRollupMessage finishRollupMessage = _probe.expectMsgClass(FinishRollupMessage.class);
        assertFalse(finishRollupMessage.isFailure());
        assertEquals("metric", finishRollupMessage.getMetricName());
        assertEquals(RollupPeriod.HOURLY, finishRollupMessage.getPeriod());

        verify(_kairosDbClient, times(1)).queryMetrics(captor.capture());
        final MetricsQuery rollupQuery = captor.getAllValues().get(0);
        assertEquals("metric", rollupQuery.getMetrics().get(0).getName());
        assertEquals(lastDataPoint, rollupQuery.getStartTime());
        assertTrue(rollupQuery.getEndTime().isPresent());
        assertEquals(RollupPeriod.HOURLY.recentEndTime(_clock.instant()).minusMillis(1), rollupQuery.getEndTime().get());

        _probe.expectNoMessage();
    }

    @Test
    public void testSkipsRollupWithCurrentDataPoint() {
        final ActorRef actor = createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        final Instant lastDataPoint = RollupPeriod.HOURLY.recentEndTime(_clock.instant());

        actor.tell(
                new LastDataPointMessage.Builder()
                        .setMetricName("metric")
                        .setPeriod(RollupPeriod.HOURLY)
                        .setTags(ImmutableSet.of("tag1", "tag2"))
                        .setLastDataPointTime(lastDataPoint)
                        .build(),
                ActorRef.noSender());

        final FinishRollupMessage finishRollupMessage = _probe.expectMsgClass(FinishRollupMessage.class);
        assertFalse(finishRollupMessage.isFailure());
        assertEquals("metric", finishRollupMessage.getMetricName());
        assertEquals(RollupPeriod.HOURLY, finishRollupMessage.getPeriod());

        verifyNoMoreInteractions(_kairosDbClient);

        _probe.expectNoMessage();
    }

    @Test
    public void testSkipsRollupWithFailedLastDataPoint() {
        when(_kairosDbClient.queryMetrics(any())).thenAnswer(invocation -> {
            final CompletableFuture<MetricsQueryResponse> future = new CompletableFuture<>();
            final Object arg0 = invocation.getArguments()[0];
            final MetricsQuery query = (MetricsQuery) arg0;
            final String metricName = query.getMetrics().get(0).getName();
            final MetricsQueryResponse.QueryResult.Builder builder = new MetricsQueryResponse.QueryResult.Builder();
            builder.setName(metricName);

            future.completeExceptionally(new RuntimeException("Failure"));

            return future;
        });

        final ArgumentCaptor<MetricsQuery> captor = ArgumentCaptor.forClass(MetricsQuery.class);
        final ActorRef actor = createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        final Instant lastDataPoint = RollupPeriod.HOURLY.recentEndTime(_clock.instant()).minus(ChronoUnit.HOURS.getDuration());

        actor.tell(
                new LastDataPointMessage.Builder()
                        .setMetricName("metric")
                        .setPeriod(RollupPeriod.HOURLY)
                        .setTags(ImmutableSet.of("tag1", "tag2"))
                        .setLastDataPointTime(lastDataPoint)
                        .build(),
                ActorRef.noSender());

        final FinishRollupMessage finishRollupMessage = _probe.expectMsgClass(FinishRollupMessage.class);
        assertTrue(finishRollupMessage.isFailure());
        assertEquals("metric", finishRollupMessage.getMetricName());
        assertEquals(RollupPeriod.HOURLY, finishRollupMessage.getPeriod());

        verify(_kairosDbClient, times(1)).queryMetrics(captor.capture());
        final MetricsQuery rollupQuery = captor.getAllValues().get(0);
        assertEquals("metric", rollupQuery.getMetrics().get(0).getName());
        assertEquals(lastDataPoint, rollupQuery.getStartTime());
        assertTrue(rollupQuery.getEndTime().isPresent());
        assertEquals(RollupPeriod.HOURLY.recentEndTime(_clock.instant()).minusMillis(1), rollupQuery.getEndTime().get());

        _probe.expectNoMessage();
    }

    /**
     * Test actor class that overrides {@code getSelf()} so that messages passed back to the actor
     * can be intercepted.
     */
    public static final class TestRollupGenerator extends RollupGenerator {
        @Inject
        public TestRollupGenerator(
                final Config configuration,
                @Named("RollupsMetricsDiscovery") final ActorRef testActor,
                final KairosDbClient kairosDbClient,
                final Clock clock,
                final PeriodicMetrics metrics) {
            super(configuration, testActor, kairosDbClient, clock, metrics);
            _self = testActor;
        }

        @Override
        public ActorRef getSelf() {
            return _self;
        }

        private final ActorRef _self;
    }
}
