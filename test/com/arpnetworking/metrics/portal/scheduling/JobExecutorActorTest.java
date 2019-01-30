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
import akka.actor.Terminated;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.testkit.TestActorRef;
import akka.testkit.javadsl.TestKit;
import com.arpnetworking.commons.java.time.ManualClock;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.impl.TsdMetricsFactory;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.incubator.impl.TsdPeriodicMetrics;
import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.scheduling.impl.MapJobRepository;
import com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule;
import com.arpnetworking.metrics.portal.scheduling.mocks.DummyJob;
import com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.ConfigFactory;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultJobQuery;
import models.internal.impl.DefaultQueryResult;
import models.internal.scheduling.Job;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link JobExecutorActor}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class JobExecutorActorTest {

    private static final Instant t0 = Instant.ofEpochMilli(0);
    private static final java.time.Duration tickSize = java.time.Duration.ofSeconds(1);
    private static final Organization organization = Organization.DEFAULT;

    private static class MockableIntJobRepository extends MapJobRepository<Integer> {}

    private Injector injector;
    private MockableIntJobRepository repo;
    private ManualClock clock;
    private PeriodicMetrics periodicMetrics;
    private ActorSystem system;

    private static final AtomicLong systemNameNonce = new AtomicLong(0);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        repo = Mockito.spy(new MockableIntJobRepository());
        repo.open();

        clock = new ManualClock(t0, tickSize, ZoneId.systemDefault());

        injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(MockableIntJobRepository.class).toInstance(repo);
                bind(MetricsFactory.class).toInstance(TsdMetricsFactory.newInstance("test", "test"));
            }
        });

        periodicMetrics = new TsdPeriodicMetrics.Builder()
                .setMetricsFactory(TsdMetricsFactory.newInstance("test", "test"))
                .build();

        system = ActorSystem.create(
                "test-"+systemNameNonce.getAndIncrement(),
                ConfigFactory.parseMap(AkkaClusteringConfigFactory.generateConfiguration()));
    }

    @After
    public void tearDown() {
        system.terminate();
    }

    private DummyJob<Integer> addJobToRepo(final DummyJob<Integer> job) {
        repo.addOrUpdateJob(job, organization);
        return job;
    }

    private Props makeExecutorActorProps() {
        return JobExecutorActor.props(injector, clock);
    }

    private ActorRef makeExecutorActor() {
        return system.actorOf(makeExecutorActorProps());
    }

    private ActorRef makeAndInitializeExecutorActor(final Job<Integer> job) {
        final JobRef<Integer> ref = new JobRef.Builder<Integer>()
                .setRepositoryType(MockableIntJobRepository.class)
                .setId(job.getId())
                .setOrganization(organization)
                .build();
        final ActorRef result = makeExecutorActor();
        result.tell(new JobExecutorActor.Reload.Builder<Integer>().setJobRef(ref).build(), null);
        return result;
    }

    @Test
    public void testJobSuccess() {
        final DummyJob<Integer> j = addJobToRepo(new DummyJob.Builder<Integer>().setOneOffSchedule(t0).setResult(123).build());
        makeAndInitializeExecutorActor(j);

        Mockito.verify(repo, Mockito.timeout(1000)).jobSucceeded(
                j.getId(),
                organization,
                j.getSchedule().nextRun(Optional.empty()).get(),
                123);
    }

    @Test
    public void testJobFailure() {
        final Throwable error = new Throwable("huh");
        final DummyJob<Integer> j = addJobToRepo(new DummyJob.Builder<Integer>().setOneOffSchedule(t0).setError(error).build());
        makeAndInitializeExecutorActor(j);

        Mockito.verify(repo, Mockito.timeout(1000)).jobFailed(
                Mockito.eq(j.getId()),
                Mockito.eq(organization),
                Mockito.eq(j.getSchedule().nextRun(Optional.empty()).get()),
                Mockito.any(CompletionException.class));
    }

    @Test
    public void testJobInFutureNotRun() {
        final Job<Integer> j = addJobToRepo(new DummyJob.Builder<Integer>().setOneOffSchedule(t0.plus(Duration.ofMinutes(1))).setResult(123).build());
        makeAndInitializeExecutorActor(j);

        Mockito.verify(repo, Mockito.after(1000).never()).jobStarted(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(repo, Mockito.never()).jobSucceeded(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(repo, Mockito.never()).jobFailed(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void testOnlyExecutesOneAtATime() {
        final Instant startAt = t0.minus(ChronoUnit.HOURS.getDuration());
        final CompletableFuture<Void> blocker = new CompletableFuture<>();
        final DummyJob<Integer> job = addJobToRepo(new DummyJob.Builder<Integer>()
                .setSchedule(new PeriodicSchedule.Builder()
                        .setRunAtAndAfter(startAt)
                        .setZone(ZoneId.of("UTC"))
                        .setPeriod(ChronoUnit.MINUTES)
                        .build())
                .setResult(123)
                .setBlocker(blocker)
                .build());

        final ActorRef executor = makeAndInitializeExecutorActor(job);

        Mockito.verify(repo, Mockito.timeout(1000).times(1)).jobStarted(job.getId(), organization, startAt);

        // Now that execution has started once, execution shouldn't start until the job completes, even if the executor ticks several times
        executor.tell(JobExecutorActor.Tick.INSTANCE, null);
        executor.tell(JobExecutorActor.Tick.INSTANCE, null);
        executor.tell(JobExecutorActor.Tick.INSTANCE, null);
        executor.tell(JobExecutorActor.Tick.INSTANCE, null);
        // (ensure that the job didn't weirdly complete for some reason)
        Mockito.verify(repo, Mockito.after(1000).never()).jobSucceeded(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        // Ensure that, despite the ticks, still only a single execution for the job has ever started
        Mockito.verify(repo, Mockito.timeout(1000).times(1)).jobStarted(Mockito.eq(job.getId()), Mockito.eq(organization), Mockito.any());

        blocker.complete(null);

        // NOW we should be able to run again
        executor.tell(JobExecutorActor.Tick.INSTANCE, null);
        Mockito.verify(repo, Mockito.timeout(1000)).jobStarted(job.getId(), organization, job.getSchedule().nextRun(Optional.of(startAt)).get());
        // ...but still, only two executions should ever have started (one for t0, one for the moment after t0)
        Mockito.verify(repo, Mockito.timeout(1000).times(2)).jobStarted(Mockito.eq(job.getId()), Mockito.eq(organization), Mockito.any());
    }

}
