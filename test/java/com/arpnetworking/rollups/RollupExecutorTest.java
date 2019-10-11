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
import com.arpnetworking.kairos.client.models.DataPoint;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.typesafe.config.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
 * Test cases for the RollupGenerator actor.
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

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(_config.getString(eq("rollup.worker.pollInterval"))).thenReturn("3sec");

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
        actor.tell(new RollupExecutor.FinishRollupMessage.Builder()
                        .setRollupDefinition(new RollupDefinition.Builder()
                                .setSourceMetricName("metric")
                                .setDestinationMetricName("metric_1h")
                                .setPeriod(RollupPeriod.HOURLY)
                                .setStartTime(Instant.EPOCH)
                                .setEndTime(Instant.EPOCH.plusMillis(1))
                                .setGroupByTags(ImmutableSet.of())
                                .build()
                        )
                        .build(),
                ActorRef.noSender()
        );
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
                        .setGroupByTags(ImmutableSet.of("tag1", "tag2"))
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.EPOCH.plus(1, ChronoUnit.HOURS))
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
        assertEquals(Instant.EPOCH.plus(1, ChronoUnit.HOURS), rollupQuery.getEndTime().get());
        assertEquals(1, rollupQuery.getMetrics().size());
        final Metric metric = rollupQuery.getMetrics().get(0);
        assertEquals(1, metric.getGroupBy().size());
        assertEquals("tag", metric.getGroupBy().get(0).getName());
        assertEquals(ImmutableSet.of("tag1", "tag2"), metric.getGroupBy().get(0).getOtherArgs().get("tags"));
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
