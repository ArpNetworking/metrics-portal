package com.arpnetworking.metrics.portal.scheduling.impl;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import com.arpnetworking.commons.java.time.ManualClock;
import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.scheduling.JobRepository;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.typesafe.config.ConfigFactory;
import models.internal.Organization;
import models.internal.scheduling.Job;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import scala.concurrent.duration.Duration;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

public final class JobSchedulerTest {


    private static final Instant t0 = Instant.ofEpochMilli(0);
    private static final java.time.Duration tickSize = java.time.Duration.ofSeconds(1);
    private static final Organization organization = Organization.DEFAULT;

    private MapJobRepository<Void> repo;
    private ManualClock clock;
    private ActorSystem system;

    private static final AtomicLong systemNameNonce = new AtomicLong(0);

    @Before
    public void setUp() {
        clock = new ManualClock(t0, tickSize, ZoneId.systemDefault());
        repo = new MapJobRepository<>();
        repo.open();
        system = ActorSystem.create(
                "test-"+systemNameNonce.getAndIncrement(),
                ConfigFactory.parseMap(AkkaClusteringConfigFactory.generateConfiguration()));
    }

    @After
    public void tearDown() {
        system.terminate();
    }

    @Test
    public void testBasics() throws ClassCastException {
        Job<Void> j = new DummyJob(repo);
        repo.addOrUpdateJob(j, organization);

        TestKit tk = new TestKit(system);
        ActorRef scheduler = system.actorOf(JobScheduler.props(repo, j.getId(), organization, clock));

        scheduler.tell(JobScheduler.Tick.INSTANCE, tk.getRef());
        tk.expectMsgClass(JobScheduler.Success.class);
        assertEquals(repo.getLastRun(j.getId(), organization), Optional.of(clock.instant()));
    }

    private static final class DummyJob implements Job<Void> {
        public UUID _uuid = UUID.randomUUID();
        public JobRepository<Void> _repository;

        DummyJob(JobRepository<Void> repository) {
            _repository = repository;
        }

        @Override
        public UUID getId() {
            return _uuid;
        }

        @Override
        public Schedule getSchedule() {
            return new OneOffSchedule.Builder()
                    .setZone(ZoneId.of("+00:00"))
                    .setRunAtAndAfter(t0)
                    .build();
        }

        @Override
        public CompletionStage<Void> execute(ActorRef scheduler, Instant scheduled) {
            return CompletableFuture.completedFuture(null);
        }
    }

}
