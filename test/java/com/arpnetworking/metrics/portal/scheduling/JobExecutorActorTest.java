/*
 * Copyright 2018 Dropbox, Inc.
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
package com.arpnetworking.metrics.portal.scheduling;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.arpnetworking.commons.java.time.ManualClock;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.impl.TsdMetricsFactory;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.incubator.impl.TsdPeriodicMetrics;
import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.scheduling.impl.MapJobExecutionRepository;
import com.arpnetworking.metrics.portal.scheduling.impl.MapJobRepository;
import com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule;
import com.arpnetworking.metrics.portal.scheduling.mocks.DummyJob;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.ConfigFactory;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import models.internal.Organization;
import models.internal.scheduling.Job;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tests for {@link JobExecutorActor}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class JobExecutorActorTest {


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        _repo = Mockito.spy(new MockableIntJobRepository());
        _repo.open();

        _execRepo = Mockito.spy(new MockableIntJobExecutionRepository());
        _execRepo.open();

        _clock = new ManualClock(T_0, TICK_SIZE, ZoneId.systemDefault());

        _injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(MockableIntJobRepository.class).toInstance(_repo);
                bind(MetricsFactory.class).toInstance(TsdMetricsFactory.newInstance("test", "test"));
            }
        });

        _periodicMetrics = new TsdPeriodicMetrics.Builder()
                .setMetricsFactory(TsdMetricsFactory.newInstance("test", "test"))
                .build();

        _system = ActorSystem.create(
                "test-" + SYSTEM_NAME_NONCE.getAndIncrement(),
                ConfigFactory.parseMap(AkkaClusteringConfigFactory.generateConfiguration()));
    }

    @After
    public void tearDown() {
        _system.terminate();
    }

    private DummyJob<Integer> addJobToRepo(final DummyJob<Integer> job) {
        _repo.addOrUpdateJob(job, ORGANIZATION);
        _execRepo.close();
        return job;
    }

    private Props makeExecutorActorProps() {
        return JobExecutorActor.props(_injector, _clock, _periodicMetrics);
    }

    private ActorRef makeExecutorActor() {
        return _system.actorOf(makeExecutorActorProps());
    }

    private ActorRef makeAndInitializeExecutorActor(final Job<Integer> job) {
        final JobRef<Integer> ref = new JobRef.Builder<Integer>()
                .setRepositoryType(MockableIntJobRepository.class)
                .setExecutionRepositoryType(MockableIntJobExecutionRepository.class)
                .setId(job.getId())
                .setOrganization(ORGANIZATION)
                .build();
        final ActorRef result = makeExecutorActor();
        result.tell(new JobExecutorActor.Reload.Builder<Integer>().setJobRef(ref).build(), null);
        return result;
    }

    @Test
    public void testJobSuccess() {
        final DummyJob<Integer> j = addJobToRepo(new DummyJob.Builder<Integer>()
                .setOneOffSchedule(T_0)
                .setTimeout(Duration.ofSeconds(30))
                .setResult(123)
                .build());
        makeAndInitializeExecutorActor(j);

        Mockito.verify(_execRepo, Mockito.timeout(1000)).jobSucceeded(
                j.getId(),
                ORGANIZATION,
                j.getSchedule().nextRun(Optional.empty()).get(),
                123);
    }

    @Test
    public void testJobFailure() {
        final Throwable error = new Throwable("huh");
        final DummyJob<Integer> j = addJobToRepo(new DummyJob.Builder<Integer>()
                .setOneOffSchedule(T_0)
                .setTimeout(Duration.ofSeconds(30))
                .setError(error)
                .build());
        makeAndInitializeExecutorActor(j);

        Mockito.verify(_execRepo, Mockito.timeout(1000)).jobFailed(
                Mockito.eq(j.getId()),
                Mockito.eq(ORGANIZATION),
                Mockito.eq(j.getSchedule().nextRun(Optional.empty()).get()),
                Mockito.any(CompletionException.class));
    }

    @Test
    public void testJobInFutureNotRun() {
        final Job<Integer> j = addJobToRepo(
                new DummyJob.Builder<Integer>()
                        .setOneOffSchedule(T_0.plus(Duration.ofMinutes(1)))
                        .setTimeout(Duration.ofSeconds(30))
                        .setResult(123)
                        .build());
        makeAndInitializeExecutorActor(j);

        Mockito.verify(_execRepo, Mockito.after(1000).never()).jobStarted(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(_execRepo, Mockito.never()).jobSucceeded(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(_execRepo, Mockito.never()).jobFailed(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    @SuppressFBWarnings("NP_NONNULL_PARAM_VIOLATION")
    public void testOnlyExecutesOneAtATime() {
        final ChronoUnit period = ChronoUnit.MINUTES;
        final Instant startAt = T_0.minus(period.getDuration());
        final CompletableFuture<Void> blocker = new CompletableFuture<>();
        final DummyJob<Integer> job = addJobToRepo(new DummyJob.Builder<Integer>()
                .setTimeout(Duration.ofSeconds(30))
                .setSchedule(new PeriodicSchedule.Builder()
                        .setRunAtAndAfter(startAt)
                        .setZone(ZoneId.of("UTC"))
                        .setPeriod(period)
                        .build())
                .setResult(123)
                .setBlocker(blocker)
                .build());

        final ActorRef executor = makeAndInitializeExecutorActor(job);

        Mockito.verify(_execRepo, Mockito.timeout(1000).times(1)).jobStarted(job.getId(), ORGANIZATION, startAt);

        // Now that execution has started once, execution shouldn't start until the job completes, even if the executor ticks several times
        executor.tell(JobExecutorActor.Tick.INSTANCE, null);
        executor.tell(JobExecutorActor.Tick.INSTANCE, null);
        executor.tell(JobExecutorActor.Tick.INSTANCE, null);
        executor.tell(JobExecutorActor.Tick.INSTANCE, null);
        // (ensure that the job didn't weirdly complete for some reason)
        Mockito.verify(_execRepo, Mockito.after(1000).never()).jobSucceeded(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        // Ensure that, despite the ticks, still only a single execution for the job has ever started
        Mockito.verify(_execRepo, Mockito.timeout(1000).times(1)).jobStarted(Mockito.eq(job.getId()), Mockito.eq(ORGANIZATION), Mockito.any());

        blocker.complete(null);

        // NOW we should be able to run again; the necessary tick should have been triggered by job completion
        Mockito.verify(_execRepo, Mockito.timeout(1000))
                .jobStarted(job.getId(), ORGANIZATION, job.getSchedule().nextRun(Optional.of(startAt)).get());
        // ...but still, only two executions should ever have started (one for T_0, one for T_0+period i.e. now)
        Mockito.verify(_execRepo, Mockito.timeout(1000).times(2))
                .jobStarted(Mockito.eq(job.getId()), Mockito.eq(ORGANIZATION), Mockito.any());
    }

    private Injector _injector;
    private MockableIntJobRepository _repo;
    private MockableIntJobExecutionRepository _execRepo;
    private ManualClock _clock;
    private PeriodicMetrics _periodicMetrics;
    private ActorSystem _system;

    private static final Instant T_0 = Instant.ofEpochMilli(0);
    private static final java.time.Duration TICK_SIZE = java.time.Duration.ofSeconds(1);
    private static final Organization ORGANIZATION = TestBeanFactory.organizationFrom(TestBeanFactory.createEbeanOrganization());
    private static final AtomicLong SYSTEM_NAME_NONCE = new AtomicLong(0);

    private static class MockableIntJobRepository extends MapJobRepository<Integer> {}
    private static class MockableIntJobExecutionRepository extends MapJobExecutionRepository<Integer> {}
}
