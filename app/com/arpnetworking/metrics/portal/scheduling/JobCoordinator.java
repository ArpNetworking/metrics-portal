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

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.persistence.AbstractPersistentActorWithTimers;
import com.arpnetworking.metrics.Unit;
import com.arpnetworking.metrics.impl.BaseScale;
import com.arpnetworking.metrics.impl.BaseUnit;
import com.arpnetworking.metrics.impl.TsdUnit;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.organizations.OrganizationRepository;
import com.arpnetworking.metrics.util.PagingIterator;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.inject.Injector;
import models.internal.Organization;
import models.internal.scheduling.Job;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Optional;

/**
 * Coordinates a {@link JobRepository}'s {@link JobExecutorActor}s to ensure that exactly one actor exists for each job.
 *
 * @param <T> The type of the results of the managed actors' jobs.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class JobCoordinator<T> extends AbstractPersistentActorWithTimers {
    private final Injector _injector;
    private final Clock _clock;
    private final Class<? extends JobRepository<T>> _repositoryType;
    private final Class<? extends JobExecutionRepository<T>> _execRepositoryType;
    private final OrganizationRepository _organizationRepository;
    private final ActorRef _jobExecutorRegion;
    private final PeriodicMetrics _periodicMetrics;

    private boolean _currentlyExecuting = false;

    /**
     * Props factory.
     *
     * @param <T> The type of result produced by the {@link JobRepository}'s jobs.
     * @param injector The Guice injector to load the {@link JobRepository} from.
     * @param repositoryType The type of the repository to load.
     * @param execRepositoryType The type of the execution repository to load.
     * @param organizationRepository Provides the set of all {@link Organization}s to monitor in the repository.
     * @param jobExecutorRegion The ref to the Akka cluster-sharding region that dispatches to {@link JobExecutorActor}s.
     * @param periodicMetrics The {@link PeriodicMetrics} that this actor will use to log its metrics.
     * @return A new props to create this actor.
     */
    public static <T> Props props(
            final Injector injector,
            final Class<? extends JobRepository<T>> repositoryType,
            final Class<? extends JobExecutionRepository<T>> execRepositoryType,
            final OrganizationRepository organizationRepository,
            final ActorRef jobExecutorRegion,
            final PeriodicMetrics periodicMetrics) {
        return props(injector,
                Clock.systemUTC(),
                repositoryType,
                execRepositoryType,
                organizationRepository,
                jobExecutorRegion,
                periodicMetrics);
    }

    /**
     * Props factory.
     *
     * @param <T> The type of result produced by the {@link JobRepository}'s jobs.
     * @param injector The Guice injector to load the {@link JobRepository} from.
     * @param clock The clock the actor will use to determine when the anti-entropy process should run.
     * @param repositoryType The type of the repository to load.
     * @param execRepositoryType The type of the execution repository to load.
     * @param organizationRepository Provides the set of all {@link Organization}s to monitor in the repository.
     * @param jobExecutorRegion The ref to the Akka cluster-sharding region that dispatches to {@link JobExecutorActor}s.
     * @param periodicMetrics The {@link PeriodicMetrics} that this actor will use to log its metrics.
     * @return A new props to create this actor.
     */
    /* package-private */
    static <T> Props props(
            final Injector injector,
            final Clock clock,
            final Class<? extends JobRepository<T>> repositoryType,
            final Class<? extends JobExecutionRepository<T>> execRepositoryType,
            final OrganizationRepository organizationRepository,
            final ActorRef jobExecutorRegion,
            final PeriodicMetrics periodicMetrics) {
        return Props.create(
                JobCoordinator.class,
                () -> new JobCoordinator<>(injector,
                        clock,
                        repositoryType,
                        execRepositoryType,
                        organizationRepository,
                        jobExecutorRegion,
                        periodicMetrics));
    }

    private JobCoordinator(
            final Injector injector,
            final Clock clock,
            final Class<? extends JobRepository<T>> repositoryType,
            final Class<? extends JobExecutionRepository<T>> execRepositoryType,
            final OrganizationRepository organizationRepository,
            final ActorRef jobExecutorRegion,
            final PeriodicMetrics periodicMetrics) {
        _injector = injector;
        _clock = clock;
        _repositoryType = repositoryType;
        _execRepositoryType = execRepositoryType;
        _organizationRepository = organizationRepository;
        _jobExecutorRegion = jobExecutorRegion;
        _periodicMetrics = periodicMetrics;
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        timers().startPeriodicTimer(
                ANTI_ENTROPY_PERIODIC_TIMER_NAME,
                AntiEntropyTick.INSTANCE,
                scala.concurrent.duration.Duration.fromNanos(ANTI_ENTROPY_TICK_INTERVAL.toNanos()));
    }

    private static <T> Iterator<? extends Job<T>> getAllJobs(final JobRepository<T> repo, final Organization organization) {
        return new PagingIterator.Builder<Job<T>>()
                .setGetPage(offset -> repo.createJobQuery(organization)
                        .offset(offset)
                        .limit(JOB_QUERY_PAGE_SIZE)
                        .execute()
                        .values())
                .build();
    }

    private static <T> void runAntiEntropy(
            final Injector injector,
            final Clock clock,
            final Class<? extends JobRepository<T>> repositoryType,
            final Class<? extends JobExecutionRepository<T>> execRepositoryType,
            final OrganizationRepository organizationRepository,
            final ActorRef jobExecutorRegion,
            final PeriodicMetrics periodicMetrics,
            final ActorRef coordinator) {

        try {
            LOGGER.debug()
                    .setMessage("starting anti-entropy")
                    .addData("repositoryType", repositoryType)
                    .addData("execRepositoryType", execRepositoryType)
                    .log();

            final Instant startTime = clock.instant();
            final JobRepository<T> repo = injector.getInstance(repositoryType);
            final Iterable<? extends Organization> allOrgs = organizationRepository.query(organizationRepository.createQuery()).values();
            for (final Organization organization : allOrgs) {
                final Iterator<? extends Job<T>> allJobs = getAllJobs(repo, organization);
                allJobs.forEachRemaining(job -> {
                    final JobRef<T> ref = new JobRef.Builder<T>()
                            .setRepositoryType(repositoryType)
                            .setExecutionRepositoryType(execRepositoryType)
                            .setOrganization(organization)
                            .setId(job.getId())
                            .build();
                    jobExecutorRegion.tell(
                            new JobExecutorActor.Reload.Builder<T>()
                                    .setJobRef(ref)
                                    .setETag(job.getETag().orElse(null))
                                    .build(),
                            coordinator);
                });
            }

            // We now know that all jobs in the repo have current actors.
            // There might still be actors which don't correspond to jobs, but that's fine:
            //   they should self-terminate next time they execute.

            periodicMetrics.recordTimer(
                    "jobs/coordinator/anti_entropy/latency",
                    ChronoUnit.NANOS.between(startTime, clock.instant()),
                    Optional.of(NANOS));

            periodicMetrics.recordCounter("jobs/coordinator/anti_entropy/success", 1);

            LOGGER.debug()
                    .setMessage("finished anti-entropy")
                    .addData("repositoryType", repositoryType)
                    .addData("elapsedTimeSec", ChronoUnit.NANOS.between(startTime, clock.instant()))
                    .log();
            // CHECKSTYLE.OFF: IllegalCatch - Just for metrics
        } catch (final RuntimeException e) {
            // CHECKSTYLE.ON: IllegalCatch
            periodicMetrics.recordCounter("jobs/coordinator/anti_entropy/success", 0);
            throw e;
        } finally {
            coordinator.tell(AntiEntropyFinished.INSTANCE, coordinator);
        }

    }

    private Runnable createAntiEntropyRunnable() {
        // Explicitly capture all instance variables for good practice, even though they're final
        final Injector injector = _injector;
        final Clock clock = _clock;
        final Class<? extends JobRepository<T>> repositoryType = _repositoryType;
        final Class<? extends JobExecutionRepository<T>> execRepositoryType = _execRepositoryType;
        final OrganizationRepository organizationRepository = _organizationRepository;
        final ActorRef jobExecutorRegion = _jobExecutorRegion;
        final PeriodicMetrics periodicMetrics = _periodicMetrics;
        final ActorRef self = getSelf();
        return () -> runAntiEntropy(
                injector,
                clock,
                repositoryType,
                execRepositoryType,
                organizationRepository,
                jobExecutorRegion,
                periodicMetrics,
                self);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(AntiEntropyTick.class, message -> {

                    LOGGER.debug()
                            .setMessage("ticking")
                            .addData("repositoryType", _repositoryType)
                            .log();
                    if (_currentlyExecuting) {
                        return;
                    }

                    getContext().getSystem().scheduler().scheduleOnce(
                            scala.concurrent.duration.Duration.Zero(),
                            createAntiEntropyRunnable(),
                            getContext().dispatcher());
                })
                .match(AntiEntropyFinished.class, message -> {
                    _currentlyExecuting = false;
                })
                .build();
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
                .build();
    }

    @Override
    public String persistenceId() {
        return String.format(
                "job-coordinator-%s",
                _repositoryType.getCanonicalName());
    }

    private static final String ANTI_ENTROPY_PERIODIC_TIMER_NAME = "TICK";
    private static final Duration ANTI_ENTROPY_TICK_INTERVAL = Duration.ofHours(1);
    private static final Logger LOGGER = LoggerFactory.getLogger(JobCoordinator.class);
    private static final Unit NANOS = new TsdUnit.Builder()
            .setScale(BaseScale.NANO)
            .setBaseUnit(BaseUnit.SECOND)
            .build();
    private static final int JOB_QUERY_PAGE_SIZE = 256;

    /**
     * Internal message, telling the scheduler to run any necessary jobs.
     */
    /* package private */ static final class AntiEntropyTick {
        /* package private */ static final AntiEntropyTick INSTANCE = new AntiEntropyTick();
    }

    /**
     * Internal message, telling the scheduler that its anti-entropy routine has finished asynchronously running.
     */
    /* package private */ static final class AntiEntropyFinished {
        /* package private */ static final AntiEntropyFinished INSTANCE = new AntiEntropyFinished();
    }

}
