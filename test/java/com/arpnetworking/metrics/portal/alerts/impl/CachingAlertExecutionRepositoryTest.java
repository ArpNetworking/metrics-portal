/*
 * Copyright 2021 Dropbox, Inc.
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
package com.arpnetworking.metrics.portal.alerts.impl;

import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.alerts.AlertExecutionRepository;
import com.arpnetworking.metrics.portal.scheduling.impl.MapJobExecutionRepository;
import com.arpnetworking.notcommons.pekko.JacksonSerializer;
import com.arpnetworking.testing.SerializationTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.ConfigFactory;
import models.internal.Organization;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.impl.DefaultAlertEvaluationResult;
import models.internal.impl.DefaultOrganization;
import models.internal.scheduling.JobExecution;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * Tests for {@link CachingAlertExecutionRepository}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class CachingAlertExecutionRepositoryTest {

    private ActorSystem _actorSystem;
    private AlertExecutionRepository _repo;
    private Organization _organization;
    private TestAlertExecutionRepository _inner;

    @Before
    public void setUp() {
        final PeriodicMetrics metrics = Mockito.mock(PeriodicMetrics.class);
        _actorSystem = ActorSystem.create("TestCacheSystem", ConfigFactory.parseMap(
                // Force serialization, since this cache is intended to
                // be used across the network.
                ImmutableMap.of(
                        "pekko.actor.serialize-messages", "on",
                        "pekko.actor.serializers", ImmutableMap.of(
                                "jackson-json", "com.arpnetworking.notcommons.pekko.JacksonSerializer"
                        ),
                        "pekko.actor.serialization-bindings", ImmutableMap.of(
                                "\"com.arpnetworking.notcommons.pekko.PekkoJsonSerializable\"", "jackson-json"
                        ),
                        "pekko.persistence.journal", ImmutableMap.of(
                                "plugin", "pekko.persistence.journal.inmem",
                                "auto-start-journals", ImmutableList.of(
                                        "pekko.persistence.journal.inmem"
                                )
                        ),
                        "pekko.persistence.snapshot-store", ImmutableMap.of(
                                "plugin", "pekko.persistence.snapshot-store.local",
                                "auto-start-journals", ImmutableList.of(
                                        "pekko.persistence.snapshot-store.local"
                                )
                        )
                )
        ));
        final ObjectMapper mapper = SerializationTestUtils.createApiObjectMapper();
        JacksonSerializer.setObjectMapper(mapper);
        final ActorRef cacheActor = _actorSystem.actorOf(AlertExecutionCacheActor.props(metrics, 100, Duration.ofMinutes(1)));

        _inner = Mockito.spy(new TestAlertExecutionRepository());
        _repo = new CachingAlertExecutionRepository.Builder()
                .setInner(_inner)
                .setActorRef(cacheActor)
                .setOperationTimeout("3s")
                .build();
        _repo.open();
        _organization = new DefaultOrganization.Builder()
            .setId(UUID.randomUUID())
            .build();
    }

    @After
    public void tearDown() {
        _repo.close();
        TestKit.shutdownActorSystem(_actorSystem);
    }

    @Test
    public void testCacheFallsBackToInner() throws Exception {
        final UUID jobId = UUID.randomUUID();
        final Instant scheduled = Instant.now();
        final AlertEvaluationResult result = newResult();

        // Write directly to the wrapped repo (not the cache)
        _inner.jobStarted(jobId, _organization, scheduled);
        _inner.jobSucceeded(jobId, _organization, scheduled, result);

        // The wrapper should fall back and still read the execution
        final Optional<JobExecution.Success<AlertEvaluationResult>> outerResult =
            _repo.getLastSuccess(jobId, _organization)
                .toCompletableFuture()
                .get();

        Mockito.verify(_inner, times(1)).getLastSuccess(eq(jobId), eq(_organization));
        assertThat(outerResult.isPresent(), is(true));
        assertThat(outerResult.get().getJobId(), is(jobId));
        assertThat(outerResult.get().getScheduled(), is(scheduled));
        assertThat(outerResult.get().getResult(), is(result));

        // A second call should hit the cache
        _repo.getLastSuccess(jobId, _organization)
                .toCompletableFuture()
                .get();
        Mockito.verify(_inner, times(1)).getLastSuccess(eq(jobId), eq(_organization));
    }

    @Test
    public void testGetLastSuccessWritesToCache() throws ExecutionException, InterruptedException {
        final UUID jobId = UUID.randomUUID();
        final Instant scheduled = Instant.now();
        final AlertEvaluationResult result = newResult();

        _repo.jobStarted(jobId, _organization, scheduled);
        _repo.jobSucceeded(jobId, _organization, scheduled, result);

        final Optional<JobExecution.Success<AlertEvaluationResult>> cachedResult =
            _repo.getLastSuccess(jobId, _organization)
                .toCompletableFuture()
                .get();

        Mockito.verify(_inner, never()).getLastSuccess(eq(jobId), eq(_organization));

        final Optional<JobExecution.Success<AlertEvaluationResult>> innerResult =
            _inner.getLastSuccess(jobId, _organization)
                .toCompletableFuture()
                .get();

        assertThat(innerResult.isPresent(), is(true));

        assertThat("cached and direct access should agree", innerResult, is(equalTo(cachedResult)));
        assertThat("cached and direct access should agree", innerResult, is(equalTo(cachedResult)));
    }

    @Test
    public void testGetLastSuccessBatchUsesCache() throws Exception {
        final int numJobs = 10;
        final int numCachedJobs = 5;
        final ImmutableList.Builder<UUID> jobIdsBuilder = ImmutableList.builder();
        final ImmutableList.Builder<UUID> cachedIdsBuilder = ImmutableList.builder();
        final ImmutableList.Builder<UUID> notCachedIdsBuilder = ImmutableList.builder();
        for (int i = 0; i < numJobs; i++) {
            final UUID jobId = UUID.randomUUID();
            final Instant scheduled = Instant.now();
            final AlertEvaluationResult result = newResult();

            if (i < numCachedJobs) {
                _repo.jobStarted(jobId, _organization, scheduled);
                _repo.jobSucceeded(jobId, _organization, scheduled, result);
                cachedIdsBuilder.add(jobId);
            } else {
                // Only call the inner repo
                _inner.jobStarted(jobId, _organization, scheduled);
                _inner.jobSucceeded(jobId, _organization, scheduled, result);
                notCachedIdsBuilder.add(jobId);
            }
            jobIdsBuilder.add(jobId);
        }
        final List<UUID> jobIds = jobIdsBuilder.build();
        final List<UUID> cachedIds = cachedIdsBuilder.build();
        final List<UUID> notCachedIds = notCachedIdsBuilder.build();

        final Map<UUID, JobExecution.Success<AlertEvaluationResult>> cachedResults =
            _repo.getLastSuccessBatch(cachedIds, _organization, LocalDate.now())
                .toCompletableFuture()
                .get();
        assertThat(cachedResults.keySet(), containsInAnyOrder(cachedIds.toArray()));

        Mockito.verify(_inner, never().description("Should have read all results from cache"))
            .getLastSuccessBatch(any(), any(), any());
        Mockito.reset(_inner);

        final Map<UUID, JobExecution.Success<AlertEvaluationResult>> mixedResults =
            _repo.getLastSuccessBatch(jobIds, _organization, LocalDate.now())
                .toCompletableFuture()
                .get();
        assertThat(mixedResults.keySet(), containsInAnyOrder(jobIds.toArray()));

        Mockito.verify(_inner, times(1).description("Should have read some results from inner"))
            .getLastSuccessBatch(eq(notCachedIds), eq(_organization), any());
        Mockito.reset(_inner);

        final Map<UUID, JobExecution.Success<AlertEvaluationResult>> allCached =
            _repo.getLastSuccessBatch(jobIds, _organization, LocalDate.now())
                .toCompletableFuture()
                .get();
        assertThat(allCached.keySet(), containsInAnyOrder(jobIds.toArray()));
        assertThat(allCached, is(equalTo(mixedResults)));

        Mockito.verify(_inner, never().description("Should have read all results from cache"))
            .getLastSuccessBatch(any(), any(), any());
    }

    private AlertEvaluationResult newResult() {
        return new DefaultAlertEvaluationResult.Builder()
            .setFiringTags(ImmutableList.of())
            .setSeriesName("testSeries")
            .setQueryStartTime(Instant.now())
            .setQueryEndTime(Instant.now())
            .build();
    }

    private static class TestAlertExecutionRepository
            extends MapJobExecutionRepository<AlertEvaluationResult>
            implements AlertExecutionRepository {}
}
