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
import akka.testkit.TestActorRef;
import com.arpnetworking.commons.java.time.ManualClock;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.impl.TsdMetricsFactory;
import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.ConfigFactory;
import models.internal.Organization;
import models.internal.scheduling.Job;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link JobExecutorActor}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class JobExecutorActorTest {

    private static class MockableJobRepository implements JobRepository<UUID> {
         @Override public void open() {}
         @Override public void close() {}
         @Override public Optional<Job<UUID>> getJob(final UUID id, final Organization organization) { return Optional.empty(); }
         @Override public Optional<Instant> getLastRun(final UUID id, final Organization organization) throws NoSuchElementException { return Optional.empty(); }
         @Override public void jobStarted(final UUID id, final Organization organization, final Instant scheduled) {}
         @Override public void jobSucceeded(final UUID id, final Organization organization, final Instant scheduled, final UUID result) {}
         @Override public void jobFailed(final UUID id, final Organization organization, final Instant scheduled, final Throwable error) {}
    }


    private static final Instant t0 = Instant.ofEpochMilli(0);
    private static final java.time.Duration tickSize = java.time.Duration.ofSeconds(1);
    private static final Organization organization = Organization.DEFAULT;

    private Injector injector;
    @Mock
    private MockableJobRepository repo;
    private ManualClock clock;
    private ActorSystem system;

    private static final AtomicLong systemNameNonce = new AtomicLong(0);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(MockableJobRepository.class).toInstance(repo);
                bind(MetricsFactory.class).toInstance(TsdMetricsFactory.newInstance("test", "test"));
            }
        });

        clock = new ManualClock(t0, tickSize, ZoneId.systemDefault());

        system = ActorSystem.create(
                "test-"+systemNameNonce.getAndIncrement(),
                ConfigFactory.parseMap(AkkaClusteringConfigFactory.generateConfiguration()));
    }

    @After
    public void tearDown() {
        system.terminate();
    }

    private <T> void addJobToRepo(final Job<T> job) {
        Mockito.doReturn(Optional.of(job)).when(repo).getJob(job.getId(), organization);
        Mockito.doReturn(Optional.empty()).when(repo).getLastRun(job.getId(), organization);
    }

    private SuccessfulJob makeSuccessfulJob() {
        return makeSuccessfulJob(t0);
    }
    private SuccessfulJob makeSuccessfulJob(final Instant runAt) {
        SuccessfulJob result = new SuccessfulJob(runAt);
        addJobToRepo(result);
        return result;
    }

    private FailingJob makeFailingJob() {
        FailingJob result = new FailingJob(new Throwable("huh, something went wrong"));
        addJobToRepo(result);
        return result;
    }

    private Props makeExecutorActorProps(final Job<UUID> job) {
        final JobRef<UUID> ref = new JobRef.Builder<UUID>()
                .setRepositoryType(MockableJobRepository.class)
                .setId(job.getId())
                .setOrganization(organization)
                .build();
        return JobExecutorActor.props(injector, ref, clock);
    }

    private ActorRef makeExecutorActor(final Job<UUID> job) {
        return system.actorOf(makeExecutorActorProps(job));
    }

    @Test
    public void testJobSuccess() {
        final Job<UUID> j = makeSuccessfulJob();
        final ActorRef scheduler = makeExecutorActor(j);

        scheduler.tell(JobExecutorActor.Tick.INSTANCE, null);

        Mockito.verify(repo, Mockito.timeout(1000)).jobSucceeded(
                j.getId(),
                organization,
                j.getSchedule().nextRun(Optional.empty()).get(),
                j.getId());
    }

    @Test
    public void testJobFailure() {
        final FailingJob j = makeFailingJob();

        final ActorRef scheduler = makeExecutorActor(j);

        scheduler.tell(JobExecutorActor.Tick.INSTANCE, null);
        Mockito.verify(repo, Mockito.timeout(1000)).jobFailed(
                j.getId(),
                organization,
                j.getSchedule().nextRun(Optional.empty()).get(),
                j._error);
    }

    @Test
    public void testJobInFutureNotRun() {
        final Job<UUID> j = makeSuccessfulJob(t0.plus(Duration.ofMinutes(1)));

        final ActorRef scheduler = makeExecutorActor(j);

        scheduler.tell(JobExecutorActor.Tick.INSTANCE, null);
        Mockito.verify(repo, Mockito.after(1000).never()).jobStarted(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(repo, Mockito.never()).jobSucceeded(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(repo, Mockito.never()).jobFailed(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void testExtraTicks() {
        final TestActorRef<JobExecutorActor<UUID>> testActor = TestActorRef.create(system, makeExecutorActorProps(makeSuccessfulJob()));
        final JobExecutorActor<UUID> scheduler = testActor.underlyingActor();
        Duration jTickInterval = Duration.ofNanos(JobExecutorActor.TICK_INTERVAL.toNanos());
        assertEquals(
                scala.concurrent.duration.Duration.Zero(),
                scheduler.timeUntilNextTick(t0.minus(Duration.ofDays(1))));
        assertEquals(
                JobExecutorActor.TICK_INTERVAL.div(2),
                scheduler.timeUntilNextTick(t0.plus(jTickInterval.dividedBy(2))));
        assertEquals(
                JobExecutorActor.TICK_INTERVAL,
                scheduler.timeUntilNextTick(t0.plus(Duration.ofDays(1))));
    }

    private static abstract class DummyJob implements Job<UUID> {
        private final UUID _uuid = UUID.randomUUID();
        private final Instant _runAt;

        DummyJob() {
            this(t0);
        }
        DummyJob(final Instant runAt) {
            _runAt = runAt;
        }

        @Override
        public UUID getId() {
            return _uuid;
        }

        @Override
        public Schedule getSchedule() {
            return new OneOffSchedule.Builder()
                    .setRunAtAndAfter(_runAt)
                    .build();
        }
    }

    private static final class SuccessfulJob extends DummyJob {
        SuccessfulJob(final Instant runAt) {super(runAt);}
        @Override
        public CompletionStage<UUID> execute(ActorRef scheduler, Instant scheduled) {
            return CompletableFuture.completedFuture(getId());
        }
    }

    private static final class FailingJob extends DummyJob {
        private final Throwable _error;

        FailingJob(final Throwable error) {
            super();
            _error = error;
        }

        @Override
        public CompletionStage<UUID> execute(ActorRef scheduler, Instant scheduled) {
            CompletableFuture<UUID> result = new CompletableFuture<>();
            result.completeExceptionally(_error);
            return result;
        }
    }

}
