/*
 * Copyright 2019 Dropbox, Inc.
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

import com.arpnetworking.commons.java.time.ManualClock;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.PekkoClusteringConfigFactory;
import com.arpnetworking.metrics.portal.organizations.OrganizationRepository;
import com.arpnetworking.metrics.portal.organizations.impl.DefaultOrganizationRepository;
import com.arpnetworking.metrics.portal.scheduling.impl.MapJobExecutionRepository;
import com.arpnetworking.metrics.portal.scheduling.impl.MapJobRepository;
import com.arpnetworking.metrics.portal.scheduling.mocks.DummyJob;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.ConfigFactory;
import models.internal.Organization;
import models.internal.scheduling.Job;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tests for {@link JobCoordinator}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class JobCoordinatorTest {

    private static final Instant T0 = Instant.ofEpochMilli(0);
    private static final java.time.Duration TICK_SIZE = java.time.Duration.ofMinutes(1);

    private Injector _injector;
    private MockableIntJobRepository _repo;
    private ManualClock _clock;
    private TestKit _messageExtractor;
    private ActorSystem _system;
    private OrganizationRepository _organizationRepo;
    private Organization _organization;
    @Mock
    private PeriodicMetrics _periodicMetrics;

    private static final AtomicLong SYSTEM_NAME_NONCE = new AtomicLong(0);
    private AutoCloseable _mocks;

    @Before
    public void setUp() {
        _mocks = MockitoAnnotations.openMocks(this);
        _repo = Mockito.spy(new MockableIntJobRepository());
        _repo.open();

        _organizationRepo = new DefaultOrganizationRepository();
        _organizationRepo.open();

        _organization = _organizationRepo.query(_organizationRepo.createQuery()).values().get(0);

        _clock = new ManualClock(T0, TICK_SIZE, ZoneId.systemDefault());

        _injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(MockableIntJobRepository.class).toInstance(_repo);
                bind(Clock.class).toInstance(_clock);
            }
        });

        _system = ActorSystem.create(
                "test-" + SYSTEM_NAME_NONCE.getAndIncrement(),
                ConfigFactory.parseMap(PekkoClusteringConfigFactory.generateConfiguration()));

        _messageExtractor = new TestKit(_system);
    }

    @After
    public void tearDown() {
        _system.terminate();
        if (_mocks != null) {
            try {
                _mocks.close();
                // CHECKSTYLE.OFF: IllegalCatch - Ignore all errors when closing the mock
            } catch (final Exception ignored) { }
                // CHECKSTYLE.ON: IllegalCatch
        }
    }

    private DummyJob<Integer> addJobToRepo(final DummyJob<Integer> job) {
        _repo.addOrUpdateJob(job, _organization);
        return job;
    }

    private JobRef<Integer> makeRef(final Job<Integer> job) {
        return new JobRef.Builder<Integer>()
                .setId(job.getId())
                .setOrganization(_organization)
                .setRepositoryType(MockableIntJobRepository.class)
                .setExecutionRepositoryType(MockableIntJobExecutionRepository.class)
                .build();
    }

    private Props makeCoordinatorActorProps() {
        return JobCoordinator.props(
                _injector,
                _clock,
                MockableIntJobRepository.class,
                MockableIntJobExecutionRepository.class,
                _organizationRepo,
                _messageExtractor.getRef(),
                _periodicMetrics);
    }

    private void makeCoordinatorActor() {
        _system.actorOf(makeCoordinatorActorProps());
    }

    @Test
    public void testRunsAntiEntropy() {
        final Job<Integer> job1 = addJobToRepo(new DummyJob.Builder<Integer>()
                .setId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .setTimeout(Duration.ofSeconds(30))
                .setOneOffSchedule(T0)
                .setResult(123)
                .build());
        final Job<Integer> job2 = addJobToRepo(new DummyJob.Builder<Integer>()
                .setId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .setTimeout(Duration.ofSeconds(30))
                .setOneOffSchedule(T0)
                .setResult(456)
                .build());

        // start the actor which will trigger an initial anti-entropy run.
        makeCoordinatorActor();

        _messageExtractor.expectMsg(Duration.ofSeconds(15), new JobExecutorActor.Reload.Builder<Integer>()
                        .setJobRef(makeRef(job1))
                        .setETag(job1.getETag().orElse(null))
                        .build());

        _messageExtractor.expectMsg(Duration.ofSeconds(15), new JobExecutorActor.Reload.Builder<Integer>()
                        .setJobRef(makeRef(job2))
                        .setETag(job2.getETag().orElse(null))
                        .build());

        _messageExtractor.expectNoMessage();
    }

    private static class MockableIntJobRepository extends MapJobRepository<Integer> {}
    private static class MockableIntJobExecutionRepository extends MapJobExecutionRepository<Integer> {}
}
