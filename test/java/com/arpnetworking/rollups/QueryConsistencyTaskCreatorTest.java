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

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tests for {@link QueryConsistencyTaskCreator}.
 *
 * @author William Ehlhardt (whale at dropbox dot com)
 */
public class QueryConsistencyTaskCreatorTest {
    @Mock
    private PeriodicMetrics _periodicMetrics;
    private AutoCloseable mocks;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @After
    public void tearDown() {
        if (mocks != null) {
            try {
                mocks.close();
            } catch (final Exception ignored) { }
        }
    }

    @Test
    public void periodStreamForInterval() {
        final List<Instant> actual = QueryConsistencyTaskCreator.periodStreamForInterval(
                Instant.parse("2020-06-11T22:23:21Z"),
                Instant.parse("2020-06-12T01:02:03Z"),
                RollupPeriod.HOURLY).collect(Collectors.toList());

        Assert.assertEquals(Arrays.asList(
                Instant.parse("2020-06-11T22:00:00Z"),
                Instant.parse("2020-06-11T23:00:00Z"),
                Instant.parse("2020-06-12T00:00:00Z"),
                Instant.parse("2020-06-12T01:00:00Z")), actual);
    }
    @Test
    public void smokeTest() {
        final ActorSystem system = ActorSystem.create(
                "test-" + UUID.randomUUID(),
                ConfigFactory.parseMap(AkkaClusteringConfigFactory.generateConfiguration()));

        final TestKit testKit = new TestKit(system);
        new QueryConsistencyTaskCreator(1, testKit.getRef(), _periodicMetrics)
                .accept(new MetricsQuery.Builder()
                        .setStartTime(Instant.parse("2020-06-01T01:02:03Z"))
                        .setEndTime(Instant.parse("2020-06-01T01:02:03Z"))
                        .setMetrics(ImmutableList.of(
                                new Metric.Builder()
                                        .setName("not_a_rollup")
                                        .build(),
                                new Metric.Builder()
                                        .setName("my_rollup_1h")
                                        .build()
                        ))
                        .build()
                );
        testKit.expectMsg(new ConsistencyChecker.Task.Builder()
                .setSourceMetricName("my_rollup")
                .setRollupMetricName("my_rollup_1h")
                .setStartTime(Instant.parse("2020-06-01T01:00:00Z"))
                .setPeriod(RollupPeriod.HOURLY)
                .setTrigger(ConsistencyChecker.Task.Trigger.QUERIED)
                .build()
        );
        testKit.expectNoMessage(); // definitely don't want a task for the other metric
    }
}
