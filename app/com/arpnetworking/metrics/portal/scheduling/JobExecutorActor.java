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

import akka.actor.AbstractActorWithTimers;
import akka.actor.PoisonPill;
import akka.actor.Props;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.incubator.impl.TsdPeriodicMetrics;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.inject.Injector;
import models.internal.Organization;
import models.internal.scheduling.Job;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * An actor that executes {@link Job}s.
 *
 * @param <T> The type of result produced by the {@link Job}s.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class JobExecutorActor<T> extends AbstractActorWithTimers {

    private final Injector _injector;
    private final JobRef<T> _jobRef;
    private final Clock _clock;
    private PeriodicMetrics _periodicMetrics;

    /**
     * Props factory.
     *
     * @param <T> The type of result produced by the {@link JobRef}'s job.
     * @param injector The Guice injector to use to load the {@link JobRepository} referenced by the {@link JobRef}.
     * @param jobRef The job to intermittently execute.
     * @return A new props to create this actor.
     */
    public static <T> Props props(final Injector injector, final JobRef<T> jobRef) {
        return props(injector, jobRef, Clock.systemUTC());
    }

    /**
     * Props factory.
     *
     * @param <T> The type of result produced by the {@link JobRef}'s job.
     * @param injector The Guice injector to use to load the {@link JobRepository} referenced by the {@link JobRef}.
     * @param jobRef The job to intermittently execute.
     * @param clock The clock the scheduler will use, when it ticks, to determine whether it's time to run the next job(s) yet.
     * @return A new props to create this actor.
     */
    protected static <T> Props props(final Injector injector, final JobRef<T> jobRef, final Clock clock) {
        return Props.create(JobExecutorActor.class, () -> new JobExecutorActor<>(injector, jobRef, clock));
    }

    private JobExecutorActor(final Injector injector, final JobRef<T> jobRef, final Clock clock) {
        _injector = injector;
        _jobRef = jobRef;
        _clock = clock;
        _periodicMetrics = new TsdPeriodicMetrics.Builder()
                .setMetricsFactory(injector.getInstance(MetricsFactory.class))
                .build();
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        timers().startSingleTimer("TICK", Tick.INSTANCE, TICK_INTERVAL);
    }

    /**
     * Returns the time until the actor should next wake up.
     *
     * @param timeToAwaken The next time the job should run.
     * @return The time until we should wake up. Non-negative.
     */
    /* package private */ FiniteDuration timeUntilNextTick(final Instant timeToAwaken) {
        final FiniteDuration delta = Duration.fromNanos(ChronoUnit.NANOS.between(_clock.instant(), timeToAwaken));
        if (delta.lt(Duration.Zero())) {
            return Duration.Zero();
        } else if (delta.lt(TICK_INTERVAL)) {
            return delta;
        } else {
            return TICK_INTERVAL;
        }
    }

    private void scheduleNextTick(final Instant wakeUpBy) {
        timers().startSingleTimer("TICK", Tick.INSTANCE, timeUntilNextTick(wakeUpBy));
    }

    private void executeAndScheduleNextTick(
            final JobRepository<T> repo,
            final Organization org,
            final Job<T> job,
            final Instant scheduled
    ) {
        final Optional<Instant> nextScheduled = job.getSchedule().nextRun(Optional.of(scheduled));
        job.execute(getSelf(), scheduled)
                .handle((result, error) -> {
                    if (error == null) {
                        repo.jobSucceeded(job.getId(), org, scheduled, result);
                    } else {
                        repo.jobFailed(job.getId(), org, scheduled, error);
                    }
                    return null;
                })
                .thenApply(whatever -> {
                    nextScheduled.ifPresent(this::scheduleNextTick);
                    return null;
                });
        repo.jobStarted(job.getId(), org, scheduled);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Tick.class, message -> {
                    _periodicMetrics.recordCounter("job-executor-actor-ticks", 1);

                    final JobRepository<T> repo = _jobRef.getRepository(_injector);
                    final Optional<Job<T>> job = _jobRef.get(_injector);
                    if (!job.isPresent()) {
                        LOGGER.warn()
                                .setMessage("no such job")
                                .addData("jobRef", _jobRef)
                                .log();
                        getSelf().tell(PoisonPill.getInstance(), getSelf());
                        return;
                    }

                    final UUID id = _jobRef.getJobId();
                    final Organization org = _jobRef.getOrganization();
                    final Optional<Instant> lastRun = repo.getLastRun(id, org);
                    final Optional<Instant> nextRun = job.get().getSchedule().nextRun(lastRun);
                    if (!nextRun.isPresent()) {
                        LOGGER.info()
                                .setMessage("job has no more scheduled runs")
                                .addData("jobRef", _jobRef)
                                .addData("schedule", job.get().getSchedule())
                                .addData("lastRun", lastRun)
                                .log();
                        getSelf().tell(PoisonPill.getInstance(), getSelf());
                        return;
                    }

                    if (nextRun.get().isBefore(_clock.instant())) {
                        executeAndScheduleNextTick(repo, org, job.get(), nextRun.get());
                    } else {
                        scheduleNextTick(nextRun.get());
                    }
                })
                .build();
    }

    /* package private */ static final FiniteDuration TICK_INTERVAL = Duration.apply(1, TimeUnit.MINUTES);
    private static final Logger LOGGER = LoggerFactory.getLogger(JobExecutorActor.class);

    /**
     * Internal message, telling the scheduler to run any necessary jobs.
     * Intended only for internal use and testing.
     */
    /* package private */ static final class Tick {
        public static final Tick INSTANCE = new Tick();
    }
}
