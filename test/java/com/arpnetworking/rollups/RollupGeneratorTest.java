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
import com.arpnetworking.kairos.client.models.DataPoint;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.TagsQuery;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
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
 * Test cases for the {@link RollupGenerator} actor.
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
    private ActorRef _testManager;

    private Clock _clock;
    private ActorSystem _system;

    private static final AtomicLong SYSTEM_NAME_NONCE = new AtomicLong(0);
    private static final int MAX_BACKFILL_PERIODS = 4;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(_config.getString(eq("rollup.fetch.backoff"))).thenReturn("5min");
        when(_config.getInt(eq("rollup.maxBackFill.periods.hourly"))).thenReturn(MAX_BACKFILL_PERIODS);
        when(_config.getInt(eq("rollup.maxBackFill.periods.daily"))).thenReturn(MAX_BACKFILL_PERIODS);
        when(_config.hasPath(eq("rollup.maxBackFill.periods.hourly"))).thenReturn(true);
        when(_config.hasPath(eq("rollup.maxBackFill.periods.daily"))).thenReturn(true);


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
                        .annotatedWith(Names.named("RollupMetricsDiscovery"))
                        .toInstance(_probe.getRef());
                bind(ActorRef.class)
                        .annotatedWith(Names.named("RollupManager"))
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

    private TestActorRef<RollupGenerator> createActor() {
        return TestActorRef.create(_system, GuiceActorCreator.props(_injector, TestRollupGenerator.class));
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

        final ArgumentCaptor<TagsQuery> captor = ArgumentCaptor.forClass(TagsQuery.class);
        final ActorRef actor = createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        actor.tell("metric", ActorRef.noSender());
        final TagNamesMessage tagNamesMessage = _probe.expectMsgClass(TagNamesMessage.class);
        assertFalse(tagNamesMessage.isFailure());
        assertEquals("metric", tagNamesMessage.getMetricName());
        assertEquals(ImmutableMultimap.of("tag1", "value1", "tag2", "value2"), tagNamesMessage.getTags());

        verify(_kairosDbClient, times(1)).queryMetricTags(captor.capture());
        final TagsQuery tagQuery = captor.getValue();
        assertEquals("metric", tagQuery.getMetrics().get(0).getName());
        assertEquals(Optional.of(Instant.ofEpochSecond(0)), tagQuery.getStartTime());
    }

    @Test
    public void testHandlesTagNamesFailure() {
        final CompletableFuture<MetricsQueryResponse> future = new CompletableFuture<>();
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
        mockKairosDbLastDatapoints(ImmutableMap.of(
            "metric", _clock.instant(),
            "metric_1h", RollupPeriod.HOURLY.recentEndTime(_clock.instant()),
            "metric_1d", RollupPeriod.DAILY.recentEndTime(_clock.instant())
        ));

        final ActorRef actor = createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        actor.tell(
                new TagNamesMessage.Builder()
                        .setMetricName("metric")
                        .setTags(ImmutableMultimap.of("tag1", "val1", "tag2", "val2"))
                        .build(),
                ActorRef.noSender());

        final LastDataPointsMessage lastDataPointMessage1 = _probe.expectMsgClass(LastDataPointsMessage.class);
        assertEquals(lastDataPointMessage1.getFailure(), Optional.empty());
        assertEquals("metric", lastDataPointMessage1.getSourceMetricName());
        assertEquals(RollupPeriod.HOURLY, lastDataPointMessage1.getPeriod());
        assertTrue(lastDataPointMessage1.getRollupLastDataPointTime().isPresent());
        assertEquals(RollupPeriod.HOURLY.recentEndTime(_clock.instant()),
                lastDataPointMessage1.getRollupLastDataPointTime().get());
        assertEquals(2, lastDataPointMessage1.getTags().size());

        final LastDataPointsMessage lastDataPointMessage2 = _probe.expectMsgClass(LastDataPointsMessage.class);
        assertEquals(lastDataPointMessage2.getFailure(), Optional.empty());
        assertEquals("metric_1h", lastDataPointMessage2.getSourceMetricName());
        assertEquals(RollupPeriod.DAILY, lastDataPointMessage2.getPeriod());
        assertTrue(lastDataPointMessage2.getRollupLastDataPointTime().isPresent());
        assertEquals(RollupPeriod.DAILY.recentEndTime(_clock.instant()),
                lastDataPointMessage2.getRollupLastDataPointTime().get());

        _probe.expectNoMessage();

        actor.tell(new FinishRollupMessage.Builder().setMetricName("metric").setPeriod(RollupPeriod.HOURLY).build(), ActorRef.noSender());
        _probe.expectNoMessage();

        actor.tell(new FinishRollupMessage.Builder().setMetricName("metric").setPeriod(RollupPeriod.DAILY).build(), ActorRef.noSender());
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);
    }

    @Test
    public void testOnlyUsesAvailableRollupsAsSources() {
        // Disable hourlys
        when(_config.hasPath(eq("rollup.maxBackFill.periods.hourly"))).thenReturn(false);

        mockKairosDbLastDatapoints(ImmutableMap.of(
                "metric", _clock.instant(),
                "metric_1h", RollupPeriod.HOURLY.recentEndTime(_clock.instant()),
                "metric_1d", RollupPeriod.DAILY.recentEndTime(_clock.instant())
        ));

        final ActorRef actor = createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        actor.tell(
                new TagNamesMessage.Builder()
                        .setMetricName("metric")
                        .setTags(ImmutableMultimap.of("tag1", "val1", "tag2", "val2"))
                        .build(),
                ActorRef.noSender());

        final LastDataPointsMessage lastDataPointMessage2 = _probe.expectMsgClass(LastDataPointsMessage.class);
        assertEquals(lastDataPointMessage2.getFailure(), Optional.empty());
        assertEquals("metric", lastDataPointMessage2.getSourceMetricName());
        assertEquals(RollupPeriod.DAILY, lastDataPointMessage2.getPeriod());
        assertTrue(lastDataPointMessage2.getRollupLastDataPointTime().isPresent());
        assertEquals(RollupPeriod.DAILY.recentEndTime(_clock.instant()),
                lastDataPointMessage2.getRollupLastDataPointTime().get());

        _probe.expectNoMessage();
    }

    @Test
    public void testLastDataPointsSingleFailure() {

        // Cause the query to fail if and only if it's for the daily rollup
        when(_kairosDbClient.queryMetrics(any())).thenAnswer(invocation -> {
            final Object arg0 = invocation.getArguments()[0];
            if (arg0 instanceof MetricsQuery) {
                final MetricsQuery query = (MetricsQuery) arg0;
                final boolean hasFailing = query.getMetrics()
                        .stream()
                        .map(Metric::getName)
                        .anyMatch(name -> name.equals("metric_1d"));
                if (hasFailing) {
                    final CompletableFuture<MetricsQueryResponse> future = new CompletableFuture<>();
                    future.completeExceptionally(new Exception("Failure"));
                    return future;
                }
                final ImmutableList<MetricsQueryResponse.Query> queries = query.getMetrics().stream().map(metric -> {
                    final String metricName = metric.getName();
                    final MetricsQueryResponse.QueryResult queryResult = new MetricsQueryResponse.QueryResult.Builder()
                            .setName(metricName)
                            .setValues(ImmutableList.of(
                                    new DataPoint.Builder()
                                            .setTime(RollupPeriod.HOURLY.recentEndTime(_clock.instant()))
                                            .setValue(0.0)
                                            .build()
                            )).build();
                    return new MetricsQueryResponse.Query.Builder()
                            .setResults(ImmutableList.of(queryResult))
                            .build();
                }).collect(ImmutableList.toImmutableList());
                return CompletableFuture.completedFuture(new MetricsQueryResponse.Builder()
                        .setQueries(queries)
                        .build()
                );
            }
            return null;
        });

        final ArgumentCaptor<MetricsQuery> captor = ArgumentCaptor.forClass(MetricsQuery.class);
        final ActorRef actor = createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        actor.tell(
                new TagNamesMessage.Builder()
                        .setMetricName("metric")
                        .setTags(ImmutableMultimap.of("tag1", "val1", "tag2", "val2"))
                        .build(),
                ActorRef.noSender());
        final Instant expectedStartTime = RollupPeriod.HOURLY.recentEndTime(_clock.instant())
                .minus(RollupPeriod.HOURLY.periodCountToDuration(MAX_BACKFILL_PERIODS));
        _probe.awaitAssert(() ->
                verify(_kairosDbClient, times(2)).queryMetrics(captor.capture())
        );
        final MetricsQuery hourlyQuery = captor.getAllValues().get(0);
        final List<String> metricNames = hourlyQuery.getMetrics()
                .stream()
                .map(Metric::getName)
                .collect(ImmutableList.toImmutableList());
        assertTrue(metricNames.contains("metric_1h"));
        assertEquals(
                Optional.of(expectedStartTime),
                hourlyQuery.getStartTime());
        assertEquals(RollupPeriod.HOURLY.recentEndTime(_clock.instant()), hourlyQuery.getEndTime().get());

        // Hourly should be unaffected
        final LastDataPointsMessage lastDataPointMessage1 = _probe.expectMsgClass(LastDataPointsMessage.class);
        assertEquals(Optional.empty(), lastDataPointMessage1.getFailure());
        assertEquals("metric", lastDataPointMessage1.getSourceMetricName());
        assertEquals(RollupPeriod.HOURLY, lastDataPointMessage1.getPeriod());
        assertTrue(lastDataPointMessage1.getRollupLastDataPointTime().isPresent());
        assertEquals(RollupPeriod.HOURLY.recentEndTime(_clock.instant()),
                lastDataPointMessage1.getRollupLastDataPointTime().get());

        // Daily should be marked as failure
        final LastDataPointsMessage lastDataPointMessage2 = _probe.expectMsgClass(LastDataPointsMessage.class);
        assertTrue(lastDataPointMessage2.isFailure());
        assertEquals("metric_1h", lastDataPointMessage2.getSourceMetricName());
        assertEquals(RollupPeriod.DAILY, lastDataPointMessage2.getPeriod());
        assertEquals("Failure", lastDataPointMessage2.getFailure().orElse(null).getMessage());

        _probe.expectNoMessage();
    }

    @Test
    public void testSendsRollupWithNoLastDataPoint() {
        final ActorRef actor = createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        actor.tell(
                new LastDataPointsMessage.Builder()
                        .setSourceMetricName("metric")
                        .setRollupMetricName("metric_1h")
                        .setPeriod(RollupPeriod.HOURLY)
                        .setTags(ImmutableMultimap.of("tag1", "val1", "tag2", "val2"))
                        .setSourceLastDataPointTime(_clock.instant())
                        .setRollupLastDataPointTime(null)
                        .build(),
                ActorRef.noSender());

        Instant startTime = RollupPeriod.HOURLY.recentEndTime(_clock.instant())
                .minus(RollupPeriod.HOURLY.periodCountToDuration(MAX_BACKFILL_PERIODS));

        for (int i = 0; i < MAX_BACKFILL_PERIODS; i++) {
            final RollupDefinition rollupDef = _probe.expectMsgClass(RollupDefinition.class);
            assertEquals("metric", rollupDef.getSourceMetricName());
            assertEquals("metric_1h", rollupDef.getDestinationMetricName());
            assertEquals(RollupPeriod.HOURLY, rollupDef.getPeriod());
            assertEquals(startTime, rollupDef.getStartTime());
            assertEquals(startTime.plus(RollupPeriod.HOURLY.periodCountToDuration(1)).minusMillis(1),
                    rollupDef.getEndTime());
            startTime = startTime.plus(RollupPeriod.HOURLY.periodCountToDuration(1));
        }

        final FinishRollupMessage finishRollupMessage = _probe.expectMsgClass(FinishRollupMessage.class);
        assertFalse(finishRollupMessage.isFailure());
        assertEquals("metric", finishRollupMessage.getMetricName());
        assertEquals(RollupPeriod.HOURLY, finishRollupMessage.getPeriod());

        _probe.expectNoMessage();
    }

    @Test
    public void testPerformRollupWithOldLastDataPoint() {
        final ActorRef actor = createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        actor.tell(
                new LastDataPointsMessage.Builder()
                        .setSourceMetricName("metric")
                        .setRollupMetricName("metric_1h")
                        .setPeriod(RollupPeriod.HOURLY)
                        .setTags(ImmutableMultimap.of("tag1", "val1", "tag2", "val2"))
                        .setSourceLastDataPointTime(_clock.instant())
                        .setRollupLastDataPointTime(Instant.EPOCH)
                        .build(),
                ActorRef.noSender());

        Instant startTime = RollupPeriod.HOURLY.recentEndTime(_clock.instant())
                .minus(RollupPeriod.HOURLY.periodCountToDuration(MAX_BACKFILL_PERIODS));

        for (int i = 0; i < MAX_BACKFILL_PERIODS; i++) {
            final RollupDefinition rollupDef = _probe.expectMsgClass(RollupDefinition.class);
            assertEquals("metric", rollupDef.getSourceMetricName());
            assertEquals("metric_1h", rollupDef.getDestinationMetricName());
            assertEquals(RollupPeriod.HOURLY, rollupDef.getPeriod());
            assertEquals(startTime, rollupDef.getStartTime());
            assertEquals(startTime.plus(RollupPeriod.HOURLY.periodCountToDuration(1)).minusMillis(1),
                    rollupDef.getEndTime());
            startTime = startTime.plus(RollupPeriod.HOURLY.periodCountToDuration(1));
        }

        final FinishRollupMessage finishRollupMessage = _probe.expectMsgClass(FinishRollupMessage.class);
        assertFalse(finishRollupMessage.isFailure());
        assertEquals("metric", finishRollupMessage.getMetricName());
        assertEquals(RollupPeriod.HOURLY, finishRollupMessage.getPeriod());

        _probe.expectNoMessage();
    }

    @Test
    public void testPerformRollupWithRecentLastDataPoint() {
        final ActorRef actor = createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        final Instant lastDataPoint =
                _clock.instant()
                    .minus(RollupPeriod.HOURLY.periodCountToDuration(3));

        actor.tell(
                new LastDataPointsMessage.Builder()
                        .setSourceMetricName("metric")
                        .setRollupMetricName("metric_1h")
                        .setPeriod(RollupPeriod.HOURLY)
                        .setTags(ImmutableMultimap.of("tag1", "val1", "tag2", "val2"))
                        .setSourceLastDataPointTime(_clock.instant())
                        .setRollupLastDataPointTime(lastDataPoint)
                        .build(),
                ActorRef.noSender());

        Instant startTime = RollupPeriod.HOURLY.recentEndTime(_clock.instant())
                .minus(RollupPeriod.HOURLY.periodCountToDuration(2));

        for (int i = 0; i < 2; i++) {
            final RollupDefinition rollupDef = _probe.expectMsgClass(RollupDefinition.class);
            assertEquals("metric", rollupDef.getSourceMetricName());
            assertEquals("metric_1h", rollupDef.getDestinationMetricName());
            assertEquals(RollupPeriod.HOURLY, rollupDef.getPeriod());
            assertEquals(startTime, rollupDef.getStartTime());
            assertEquals(startTime.plus(RollupPeriod.HOURLY.periodCountToDuration(1)).minusMillis(1),
                    rollupDef.getEndTime());
            startTime = startTime.plus(RollupPeriod.HOURLY.periodCountToDuration(1));
        }

        final FinishRollupMessage finishRollupMessage = _probe.expectMsgClass(FinishRollupMessage.class);
        assertFalse(finishRollupMessage.isFailure());
        assertEquals("metric", finishRollupMessage.getMetricName());
        assertEquals(RollupPeriod.HOURLY, finishRollupMessage.getPeriod());

        _probe.expectNoMessage();
    }

    @Test
    public void testSkipsRollupWithCurrentDataPoint() {
        final ActorRef actor = createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        final Instant lastDataPoint = RollupPeriod.HOURLY.recentEndTime(_clock.instant())
                .minus(RollupPeriod.HOURLY.periodCountToDuration(1));

        actor.tell(
                new LastDataPointsMessage.Builder()
                        .setSourceMetricName("metric")
                        .setRollupMetricName("metric_1h")
                        .setPeriod(RollupPeriod.HOURLY)
                        .setTags(ImmutableMultimap.of("tag1", "val1", "tag2", "val2"))
                        .setSourceLastDataPointTime(_clock.instant())
                        .setRollupLastDataPointTime(lastDataPoint)
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
        final ActorRef actor = createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        actor.tell(
                new LastDataPointsMessage.Builder()
                        .setSourceMetricName("metric")
                        .setRollupMetricName("metric_1h")
                        .setPeriod(RollupPeriod.HOURLY)
                        .setTags(ImmutableMultimap.of("tag1", "val1", "tag2", "val2"))
                        .setFailure(new RuntimeException("Failure"))
                        .build(),
                ActorRef.noSender());

        final FinishRollupMessage finishRollupMessage = _probe.expectMsgClass(FinishRollupMessage.class);
        assertTrue(finishRollupMessage.isFailure());
        assertEquals("metric", finishRollupMessage.getMetricName());
        assertEquals(RollupPeriod.HOURLY, finishRollupMessage.getPeriod());

        _probe.expectNoMessage();
    }

    @Test
    public void testSkipsRollupWithEmptySourceDataPoint() {
        final ActorRef actor = createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        actor.tell(
                new LastDataPointsMessage.Builder()
                        .setSourceMetricName("metric")
                        .setRollupMetricName("metric_1h")
                        .setPeriod(RollupPeriod.HOURLY)
                        .setTags(ImmutableMultimap.of("tag1", "val1", "tag2", "val2"))
                        .setSourceLastDataPointTime(null)
                        .setRollupLastDataPointTime(null)
                        .build(),
                ActorRef.noSender());

        final FinishRollupMessage finishRollupMessage = _probe.expectMsgClass(FinishRollupMessage.class);
        assertFalse(finishRollupMessage.isFailure());
        assertEquals("metric", finishRollupMessage.getMetricName());
        assertEquals(RollupPeriod.HOURLY, finishRollupMessage.getPeriod());
    }

    @Test
    public void testOnlyBackfillsUpToLastCompletedSourcePeriod() {
        final ActorRef actor = createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        final int periodsBehind = 2;

        Instant startTime = RollupPeriod.HOURLY.recentEndTime(_clock.instant())
                .minus(RollupPeriod.HOURLY.periodCountToDuration(MAX_BACKFILL_PERIODS));

        actor.tell(
                new LastDataPointsMessage.Builder()
                        .setSourceMetricName("metric")
                        .setRollupMetricName("metric_1h")
                        .setPeriod(RollupPeriod.HOURLY)
                        .setTags(ImmutableMultimap.of("tag1", "val1", "tag2", "val2"))
                        .setSourceLastDataPointTime(startTime.plus(RollupPeriod.HOURLY.periodCountToDuration(periodsBehind)).minusMillis(1))
                        .setRollupLastDataPointTime(null)
                        .build(),
                ActorRef.noSender());

        for (int i = periodsBehind; i < MAX_BACKFILL_PERIODS; i++) {
            final RollupDefinition rollupDef = _probe.expectMsgClass(RollupDefinition.class);
            assertEquals("metric", rollupDef.getSourceMetricName());
            assertEquals("metric_1h", rollupDef.getDestinationMetricName());
            assertEquals(RollupPeriod.HOURLY, rollupDef.getPeriod());
            assertEquals(startTime, rollupDef.getStartTime());
            assertEquals(startTime.plus(RollupPeriod.HOURLY.periodCountToDuration(1)).minusMillis(1),
                    rollupDef.getEndTime());
            startTime = startTime.plus(RollupPeriod.HOURLY.periodCountToDuration(1));
        }

        final FinishRollupMessage finishRollupMessage = _probe.expectMsgClass(FinishRollupMessage.class);
        assertFalse(finishRollupMessage.isFailure());
        assertEquals("metric", finishRollupMessage.getMetricName());
        assertEquals(RollupPeriod.HOURLY, finishRollupMessage.getPeriod());

        _probe.expectNoMessage();
    }


    @Test
    public void testUnsetConfigDisablesRollupGeneration() {
        when(_config.hasPath(eq("rollup.maxBackFill.periods.hourly"))).thenReturn(false);
        when(_config.hasPath(eq("rollup.maxBackFill.periods.daily"))).thenReturn(false);

        final ActorRef actor = createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        actor.tell(
                new TagNamesMessage.Builder()
                        .setMetricName("metric")
                        .setTags(ImmutableMultimap.of("tag1", "val1", "tag2", "val2"))
                        .build(),
                ActorRef.noSender());

        // LastDataPointMessage should never be sent.
        _probe.expectNoMessage();
    }

    @Test
    public void testGetRollupTimes() {
        final RollupGenerator actor = createActor().underlyingActor();
        final Instant startOfCurrentPeriod = _clock.instant().truncatedTo(ChronoUnit.HOURS);
        final Duration hour = Duration.ofHours(1);
        final Duration minute = Duration.ofMinutes(1);

        assertEquals(
                ImmutableSet.of(),
                actor.getRollupTimes(
                        Optional.of(startOfCurrentPeriod.minus(hour)),
                        Optional.of(startOfCurrentPeriod.plus(hour)),
                        RollupPeriod.HOURLY
                )
        );
        assertEquals(
                Stream.iterate(startOfCurrentPeriod.minus(hour), t -> t.minus(hour))
                        .limit(MAX_BACKFILL_PERIODS)
                        .collect(ImmutableSet.toImmutableSet()),
                actor.getRollupTimes(
                        Optional.empty(),
                        Optional.of(startOfCurrentPeriod),
                        RollupPeriod.HOURLY
                )
        );
        assertEquals(
                Stream.iterate(startOfCurrentPeriod.minus(hour), t -> t.minus(hour))
                        .limit(MAX_BACKFILL_PERIODS)
                        .collect(ImmutableSet.toImmutableSet()),
                actor.getRollupTimes(
                        Optional.of(startOfCurrentPeriod.minus(hour.multipliedBy(99999))),
                        Optional.of(startOfCurrentPeriod),
                        RollupPeriod.HOURLY
                )
        );
        assertEquals(
                Stream.iterate(startOfCurrentPeriod.minus(hour), t -> t.minus(hour))
                        .limit(2)
                        .collect(ImmutableSet.toImmutableSet()),
                actor.getRollupTimes(
                        Optional.of(startOfCurrentPeriod.minus(hour.multipliedBy(3))),
                        Optional.of(startOfCurrentPeriod),
                        RollupPeriod.HOURLY
                )
        );
    }

    @Test
    public void testPerPeriodBackfillConfiguration() {
        final int hourlyBackfill = 4;
        final int dailyBackfill = 2;

        when(_config.getInt(eq("rollup.maxBackFill.periods.hourly"))).thenReturn(hourlyBackfill);
        when(_config.getInt(eq("rollup.maxBackFill.periods.daily"))).thenReturn(dailyBackfill);

        final ActorRef actor = createActor();
        _probe.expectMsg(RollupGenerator.FETCH_METRIC);

        final Instant lastDataPointTime =
                RollupPeriod.DAILY.recentEndTime(_clock.instant())
                .minus(RollupPeriod.DAILY.periodCountToDuration(dailyBackfill * 2));

        actor.tell(
                new LastDataPointsMessage.Builder()
                        .setSourceMetricName("metric")
                        .setRollupMetricName("metric_1d")
                        .setPeriod(RollupPeriod.DAILY)
                        .setTags(ImmutableMultimap.of("tag1", "val1", "tag2", "val2"))
                        .setSourceLastDataPointTime(_clock.instant())
                        .setRollupLastDataPointTime(lastDataPointTime)
                        .build(),
                ActorRef.noSender());

        Instant startTime = RollupPeriod.DAILY.recentEndTime(_clock.instant())
                .minus(RollupPeriod.DAILY.periodCountToDuration(dailyBackfill));
        for (int i = 0; i < dailyBackfill; i++) {
            final RollupDefinition rollupDef = _probe.expectMsgClass(RollupDefinition.class);
            assertEquals("metric", rollupDef.getSourceMetricName());
            assertEquals("metric_1d", rollupDef.getDestinationMetricName());
            assertEquals(RollupPeriod.DAILY, rollupDef.getPeriod());
            assertEquals(startTime, rollupDef.getStartTime());
            assertEquals(startTime.plus(RollupPeriod.DAILY.periodCountToDuration(1)).minusMillis(1),
                    rollupDef.getEndTime());

            startTime = startTime.plus(RollupPeriod.DAILY.periodCountToDuration(1));
        }

        _probe.expectMsgClass(FinishRollupMessage.class);

        actor.tell(
                new LastDataPointsMessage.Builder()
                        .setSourceMetricName("metric")
                        .setRollupMetricName("metric_1h")
                        .setPeriod(RollupPeriod.HOURLY)
                        .setTags(ImmutableMultimap.of("tag1", "val1", "tag2", "val2"))
                        .setSourceLastDataPointTime(_clock.instant())
                        .setRollupLastDataPointTime(lastDataPointTime)
                        .build(),
                ActorRef.noSender());

        startTime = RollupPeriod.HOURLY.recentEndTime(_clock.instant())
                .minus(RollupPeriod.HOURLY.periodCountToDuration(hourlyBackfill));
        for (int i = 0; i < hourlyBackfill; i++) {
            final RollupDefinition rollupDef = _probe.expectMsgClass(RollupDefinition.class);
            assertEquals("metric", rollupDef.getSourceMetricName());
            assertEquals("metric_1h", rollupDef.getDestinationMetricName());
            assertEquals(RollupPeriod.HOURLY, rollupDef.getPeriod());
            assertEquals(startTime, rollupDef.getStartTime());
            assertEquals(startTime.plus(RollupPeriod.HOURLY.periodCountToDuration(1)).minusMillis(1),
                    rollupDef.getEndTime());

            startTime = startTime.plus(RollupPeriod.HOURLY.periodCountToDuration(1));
        }

        _probe.expectMsgClass(FinishRollupMessage.class);
    }

    private void mockKairosDbLastDatapoints(final Map<String, Instant> metricToLastTime) {
        when(_kairosDbClient.queryMetrics(any())).thenAnswer(invocation -> {
            final Object arg0 = invocation.getArguments()[0];
            if (arg0 instanceof MetricsQuery) {
                final MetricsQuery query = (MetricsQuery) arg0;
                final ImmutableList<MetricsQueryResponse.Query> queries = query.getMetrics().stream().map(metric -> {
                    final String metricName = metric.getName();
                    final MetricsQueryResponse.QueryResult queryResult = new MetricsQueryResponse.QueryResult.Builder()
                            .setName(metricName)
                            .setValues(ImmutableList.of(new DataPoint.Builder()
                                    .setTime(metricToLastTime.get(metricName))
                                    .setValue(0.0)
                                    .build()
                            )).build();
                    return new MetricsQueryResponse.Query.Builder()
                            .setResults(ImmutableList.of(queryResult))
                            .build();
                }).collect(ImmutableList.toImmutableList());
                return CompletableFuture.completedFuture(new MetricsQueryResponse.Builder()
                        .setQueries(queries)
                        .build()
                );
            }
            return null;
        });
    }

    /**
     * Test actor class that overrides {@code getSelf()} so that messages passed back to the actor
     * can be intercepted.
     */
    public static final class TestRollupGenerator extends RollupGenerator {
        @Inject
        public TestRollupGenerator(
                final Config configuration,
                @Named("RollupMetricsDiscovery") final ActorRef testActor,
                @Named("RollupManager") final ActorRef rollupManager,
                final KairosDbClient kairosDbClient,
                final Clock clock,
                final PeriodicMetrics metrics) {
            super(configuration, testActor, rollupManager, kairosDbClient, clock, metrics);
            _self = testActor;
        }

        @Override
        public ActorRef getSelf() {
            return _self;
        }

        private final ActorRef _self;
    }
}
