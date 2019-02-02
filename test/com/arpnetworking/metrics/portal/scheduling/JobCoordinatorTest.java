package com.arpnetworking.metrics.portal.scheduling;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;
import com.arpnetworking.commons.java.time.ManualClock;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.scheduling.impl.MapJobRepository;
import com.arpnetworking.metrics.portal.scheduling.mocks.DummyJob;
import com.google.common.collect.Lists;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class JobCoordinatorTest {

    private static final Instant t0 = Instant.ofEpochMilli(0);
    private static final java.time.Duration tickSize = java.time.Duration.ofMinutes(1);
    private static final Organization organization = Organization.DEFAULT;

    private static class MockableIntJobRepository extends MapJobRepository<Integer> {}

    private Injector injector;
    private MockableIntJobRepository repo;
    private ManualClock clock;
    private TestKit messageExtractor;
    private ActorSystem system;
    @Mock
    private PeriodicMetrics periodicMetrics;

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
                bind(Clock.class).toInstance(clock);
            }
        });

        system = ActorSystem.create(
                "test-"+systemNameNonce.getAndIncrement(),
                ConfigFactory.parseMap(AkkaClusteringConfigFactory.generateConfiguration()));

        messageExtractor = new TestKit(system);
    }

    @After
    public void tearDown() {
        system.terminate();
    }

    private DummyJob<Integer> addJobToRepo(final DummyJob<Integer> job) {
        repo.addOrUpdateJob(job, organization);
        return job;
    }

    private static JobRef<Integer> makeRef(final Job<Integer> job) {
        return new JobRef.Builder<Integer>().setId(job.getId()).setOrganization(organization).setRepositoryType(MockableIntJobRepository.class).build();
    }

    private Props makeCoordinatorActorProps() {
        return JobCoordinator.props(injector, clock, MockableIntJobRepository.class, organization, messageExtractor.getRef(), periodicMetrics);
    }

    private ActorRef makeCoordinatorActor() {
        return system.actorOf(makeCoordinatorActorProps());
    }

    @Test
    public void testRunsAntiEntropy() {
        final Job<Integer> job1 = addJobToRepo(new DummyJob.Builder<Integer>()
                .setId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .setOneOffSchedule(t0)
                .setResult(123)
                .build());
        final Job<Integer> job2 = addJobToRepo(new DummyJob.Builder<Integer>()
                .setId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .setOneOffSchedule(t0)
                .setResult(456)
                .build());

        final ActorRef coordinator = makeCoordinatorActor();
        coordinator.tell(JobCoordinator.AntiEntropyTick.INSTANCE, null);

        messageExtractor.expectMsg(new JobExecutorActor.Reload.Builder<Integer>()
                        .setJobRef(makeRef(job1))
                        .setETag(job1.getETag())
                        .build());

        messageExtractor.expectMsg(new JobExecutorActor.Reload.Builder<Integer>()
                        .setJobRef(makeRef(job2))
                        .setETag(job2.getETag())
                        .build());

        messageExtractor.expectNoMsg();
    }
}
