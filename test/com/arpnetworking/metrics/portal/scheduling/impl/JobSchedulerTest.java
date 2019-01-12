package com.arpnetworking.metrics.portal.scheduling.impl;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import com.arpnetworking.commons.java.time.ManualClock;
import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.scheduling.JobRef;
import com.arpnetworking.metrics.portal.scheduling.JobRepository;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.typesafe.config.ConfigFactory;
import models.internal.Organization;
import models.internal.scheduling.Job;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class JobSchedulerTest {


    private static final Instant t0 = Instant.ofEpochMilli(0);
    private static final java.time.Duration tickSize = java.time.Duration.ofSeconds(1);
    private static final Organization organization = Organization.DEFAULT;

    @Mock
    private JobRepository<UUID> repo;
    private ManualClock clock;
    private ActorSystem system;

    private static final AtomicLong systemNameNonce = new AtomicLong(0);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
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

    @Test
    public void testJobSuccess() {
        final Job<UUID> j = makeSuccessfulJob();

        final TestKit tk = new TestKit(system);
        final ActorRef scheduler = system.actorOf(JobScheduler.props(new JobRef.Builder<UUID>().setRepository(repo).setId(j.getId()).setOrganization(organization).build(), clock));

        scheduler.tell(JobScheduler.Tick.INSTANCE, tk.getRef());
        tk.expectMsgClass(JobScheduler.Success.class);
        Mockito.verify(repo).jobSucceeded(
                j.getId(),
                organization,
                j.getSchedule().nextRun(Optional.empty()).get(),
                j.getId());
    }

    @Test
    public void testJobFailure() {
        final FailingJob j = makeFailingJob();

        final TestKit tk = new TestKit(system);
        final ActorRef scheduler = system.actorOf(JobScheduler.props(new JobRef.Builder<UUID>().setRepository(repo).setId(j.getId()).setOrganization(organization).build(), clock));

        scheduler.tell(JobScheduler.Tick.INSTANCE, tk.getRef());
        tk.expectMsgClass(JobScheduler.Failure.class);
        Mockito.verify(repo).jobFailed(
                j.getId(),
                organization,
                j.getSchedule().nextRun(Optional.empty()).get(),
                j._error);
    }

    @Test
    public void testJobInFutureNotRun() {
        final Job<UUID> j = makeSuccessfulJob(t0.plus(Duration.ofMinutes(1)));

        final TestKit tk = new TestKit(system);
        final ActorRef scheduler = system.actorOf(JobScheduler.props(new JobRef.Builder<UUID>().setRepository(repo).setId(j.getId()).setOrganization(organization).build(), clock));

        scheduler.tell(JobScheduler.Tick.INSTANCE, tk.getRef());
        tk.expectNoMsg();
        Mockito.verify(repo, Mockito.never()).jobStarted(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(repo, Mockito.never()).jobSucceeded(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(repo, Mockito.never()).jobFailed(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void testExtraTicks() {
        Duration jTickInterval = Duration.ofNanos(JobScheduler.TICK_INTERVAL.toNanos());
        Duration jSleepSlop = Duration.ofNanos(JobScheduler.SLEEP_SLOP.toNanos());
        assertEquals(
                Optional.empty(),
                JobScheduler.timeUntilExtraTick(t0, t0.plus(jTickInterval)));
        assertEquals(
                Optional.of(JobScheduler.TICK_INTERVAL.div(2).plus(JobScheduler.SLEEP_SLOP)),
                JobScheduler.timeUntilExtraTick(t0, t0.plus(jTickInterval.dividedBy(2))));
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
