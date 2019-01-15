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
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.inject.Injector;
import models.internal.Organization;
import models.internal.scheduling.Job;
import scala.Serializable;
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
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        timers().startPeriodicTimer("TICK", Tick.INSTANCE, TICK_INTERVAL);
    }

    /**
     * If we'll need an extra tick before the next naturally occurring one, returns the time until that tick should happen.
     *
     * @param now The current time.
     * @param timeToAwaken The time we want to wake up right after.
     * @return The time until we should wake up, if that's before the next tick; else {@code empty}.
     */
    protected static Optional<FiniteDuration> timeUntilExtraTick(final Instant now, final Instant timeToAwaken) {
        final FiniteDuration delta = Duration.fromNanos(ChronoUnit.NANOS.between(now, timeToAwaken));
        if (delta.lt(TICK_INTERVAL)) {
            return Optional.of(delta.plus(SLEEP_SLOP));
        } else {
            return Optional.empty();
        }
    }

    private void executeOrScheduleIfNecessary(
            final JobRepository<T> repo,
            final Organization org,
            final Job<T> job,
            final Instant scheduled
    ) {
        if (scheduled.isAfter(_clock.instant())) {
            // It's not time to execute the job yet, but it might be soon.
            // Let's ensure we wake up "exactly" when it's time to execute the job,
            //   else we might be up to 1tick late executing it.
            //   (Well-- "exactly" is a tall order, but we can at least ensure we wake up
            //    _very slightly_ late.)
            timeUntilExtraTick(_clock.instant(), scheduled).ifPresent(delta -> {
                timers().startSingleTimer("TICK_ONEOFF", Tick.INSTANCE, delta);
            });
        } else {
            final ActorRef sender = getSender();
            job.execute(getSelf(), scheduled)
                    .handle((result, error) -> {
                        if (error == null) {
                            repo.jobSucceeded(job.getId(), org, scheduled, result);
                        } else {
                            repo.jobFailed(job.getId(), org, scheduled, error);
                        }
                        return null;
                    });
            repo.jobStarted(job.getId(), org, scheduled);
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Tick.class, message -> {
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
                    final UUID id = _jobRef.getId();
                    final Organization org = _jobRef.getOrganization();
                    final Optional<Instant> nextRun = job.get().getSchedule().nextRun(repo.getLastRun(id, org));
                    if (!nextRun.isPresent()) {
                        getSelf().tell(PoisonPill.getInstance(), getSelf());
                        return;
                    }
                    executeOrScheduleIfNecessary(repo, org, job.get(), nextRun.get());
                })
                .build();
    }

    protected static final FiniteDuration TICK_INTERVAL = Duration.apply(1, TimeUnit.MINUTES);
    protected static final FiniteDuration SLEEP_SLOP = Duration.apply(10, TimeUnit.MILLISECONDS);
    private static final Logger LOGGER = LoggerFactory.getLogger(JobExecutorActor.class);

    /**
     * Internal message, telling the scheduler to run any necessary jobs.
     * Intended only for internal use and testing.
     */
    protected static final class Tick {
        public static final Tick INSTANCE = new Tick();
    }
}
