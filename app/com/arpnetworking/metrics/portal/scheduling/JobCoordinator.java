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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

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
    private final Organization _organization;
    private final ActorRef _jobExecutorRegion;
    private final Semaphore _executionMutex = new Semaphore(1);
    private final PeriodicMetrics _periodicMetrics;

    /**
     * Props factory.
     *
     * @param <T> The type of result produced by the {@link JobRepository}'s jobs.
     * @param injector The Guice injector to load the {@link JobRepository} from.
     * @param repositoryType The type of the repository to load.
     * @param organization The {@link Organization} whose jobs to coordinate.
     * @param jobExecutorRegion The ref to the Akka cluster-sharding region that dispatches to {@link JobExecutorActor}s.
     * @param periodicMetrics The {@link PeriodicMetrics} that this actor will use to log its metrics.
     * @return A new props to create this actor.
     */
    public static <T> Props props(final Injector injector,
                                  final Class<? extends JobRepository<T>> repositoryType,
                                  final Organization organization,
                                  final ActorRef jobExecutorRegion,
                                  final PeriodicMetrics periodicMetrics) {
        return props(injector, Clock.systemUTC(), repositoryType, organization, jobExecutorRegion, periodicMetrics);
    }

    /**
     * Props factory.
     *
     * @param <T> The type of result produced by the {@link JobRepository}'s jobs.
     * @param injector The Guice injector to load the {@link JobRepository} from.
     * @param clock The clock the actor will use to determine when the anti-entropy process should run.
     * @param repositoryType The type of the repository to load.
     * @param organization The {@link Organization} whose jobs to coordinate.
     * @param periodicMetrics The {@link PeriodicMetrics} that this actor will use to log its metrics.
     * @return A new props to create this actor.
     */
    /* package-private */ static <T> Props props(final Injector injector,
                                                 final Clock clock,
                                                 final Class<? extends JobRepository<T>> repositoryType,
                                                 final Organization organization,
                                                 final ActorRef jobExecutorRegion,
                                                 final PeriodicMetrics periodicMetrics) {
        return Props.create(
                JobCoordinator.class,
                () -> new JobCoordinator<>(injector, clock, repositoryType, organization, jobExecutorRegion, periodicMetrics));
    }

    private JobCoordinator(final Injector injector,
                           final Clock clock,
                           final Class<? extends JobRepository<T>> repositoryType,
                           final Organization organization,
                           final ActorRef jobExecutorRegion,
                           final PeriodicMetrics periodicMetrics) {
        _injector = injector;
        _clock = clock;
        _repositoryType = repositoryType;
        _organization = organization;
        _jobExecutorRegion = jobExecutorRegion;
        _periodicMetrics = periodicMetrics;
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        timers().startPeriodicTimer(
                "TICK",
                AntiEntropyTick.INSTANCE,
                scala.concurrent.duration.Duration.fromNanos(ANTI_ENTROPY_TICK_INTERVAL.toNanos()));
    }

    @Override
    public void postRestart(final Throwable reason) throws Exception {
        super.postRestart(reason);
        timers().startPeriodicTimer(
                "TICK",
                AntiEntropyTick.INSTANCE,
                scala.concurrent.duration.Duration.fromNanos(ANTI_ENTROPY_TICK_INTERVAL.toNanos()));
    }

    private static <T> Iterator<? extends T> flatten(final Supplier<Iterator<? extends T>> getPage) {
        return new Iterator<T>() {
            private Iterator<? extends T> _buffer;
            private boolean _exhaustedSource = false;

            private void fetch() {
                _buffer = getPage.get();
                _exhaustedSource = !_buffer.hasNext();
            }

            @Override
            public boolean hasNext() {
                if (_buffer == null) {
                    fetch();
                }
                return !_buffer.hasNext() && _exhaustedSource;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return _buffer.next();
            }
        };
    }

    private Iterator<? extends Job<T>> getAllJobs() {
        final JobRepository<T> repo = _injector.getInstance(_repositoryType);
        return flatten(new Supplier<Iterator<? extends Job<T>>>() {
            private int _offset = 0;
            @Override
            public Iterator<? extends Job<T>> get() {
                final List<? extends Job<T>> result = repo.createQuery(_organization)
                        .offset(_offset)
                        .limit(JOB_QUERY_PAGE_SIZE)
                        .execute()
                        .values();
                _offset += result.size();
                return result.iterator();
            }
        });
    }

    private Runnable buildAntiEntropyRunnable() {
        return () -> {
            if (_executionMutex.tryAcquire()) {
                try {
                    final Instant startTime = _clock.instant();
                    getAllJobs().forEachRemaining(job -> {
                        final JobRef<T> ref = new JobRef.Builder<T>()
                                .setRepositoryType(_repositoryType)
                                .setOrganization(_organization)
                                .setId(job.getId())
                                .build();
                        _jobExecutorRegion.tell(
                                new JobExecutorActor.Reload.Builder<T>()
                                        .setJobRef(ref)
                                        .setETag(job.getETag())
                                        .build(),
                                getSelf());
                    });

                    // We now know that all jobs in the repo have current actors.
                    // There might still be actors which don't correspond to jobs, but that's fine:
                    //   they should self-terminate next time they execute.

                    _periodicMetrics.recordTimer(
                            "job_coordinator_tick_time",
                            ChronoUnit.NANOS.between(startTime, _clock.instant()),
                            Optional.of(NANOS));

                } finally {
                    _executionMutex.release();
                }
            }
        };

    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(AntiEntropyTick.class, message -> {

                    LOGGER.info()
                            .setMessage("starting anti-entropy")
                            .addData("organization", _organization)
                            .addData("repositoryType", _repositoryType)
                            .log();

                    getContext().getSystem().scheduler().scheduleOnce(
                            scala.concurrent.duration.Duration.Zero(),
                            buildAntiEntropyRunnable(),
                            getContext().getSystem().dispatcher()); // TODO(spencerpearson): probably not the right dispatcher

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
        return "com.arpnetworking.metrics.portal.scheduling.JobCoordinator:"
                + "repositoryType=" + _repositoryType + "; "
                + "organizationId=" + _organization.getId();
    }
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
    protected static final class AntiEntropyTick {
        /* package private */ static final AntiEntropyTick INSTANCE = new AntiEntropyTick();
    }

}
