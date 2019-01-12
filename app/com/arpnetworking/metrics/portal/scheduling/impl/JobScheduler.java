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
package com.arpnetworking.metrics.portal.scheduling.impl;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.pattern.PatternsCS;
import com.arpnetworking.metrics.portal.scheduling.JobRef;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import models.internal.scheduling.Job;
import scala.Serializable;
import scala.concurrent.duration.Duration;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * An actor that executes {@link Job}s.
 *
 * @param <T> The type of result produced by the {@link Job}s.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class JobScheduler<T> extends AbstractActorWithTimers {

    private final JobRef<T> _jobRef;
    private final Clock _clock;

    /**
     * Props factory.
     *
     * @param <T> The type of result produced by the {@link JobRef}'s job.
     * @param jobRef The job to intermittently execute.
     * @return A new props to create this actor.
     */
    public static <T> Props props(final JobRef<T> jobRef) {
        return props(jobRef, Clock.systemUTC());
    }

    /**
     * Props factory.
     *
     * @param <T> The type of result produced by the {@link JobRef}'s job.
     * @param jobRef The job to intermittently execute.
     * @param clock The clock the scheduler will use, when it ticks, to determine whether it's time to run the next job(s) yet.
     * @return A new props to create this actor.
     */
    protected static <T> Props props(final JobRef<T> jobRef, final Clock clock) {
        return Props.create(JobScheduler.class, () -> new JobScheduler<>(jobRef, clock));
    }

    private JobScheduler(final JobRef<T> jobRef, final Clock clock) {
        _jobRef = jobRef;
        _clock = clock;
        timers().startPeriodicTimer("TICK", new Tick(), Duration.apply(1, TimeUnit.MINUTES));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Tick.class, message -> {
                    final Optional<Job<T>> maybeJob = _jobRef.get();
                    if (!maybeJob.isPresent()) {
                        LOGGER.error()
                                .setMessage("no such job")
                                .addData("jobRef", _jobRef)
                                .log();
                        return;
                    }
                    final Job<T> job = maybeJob.get();
                    final Optional<Instant> maybeNextRun = job.getSchedule().nextRun(_jobRef.getLastRun());
                    if (!maybeNextRun.isPresent()) {
                        getSelf().tell(PoisonPill.getInstance(), getSelf());
                        return;
                    }
                    final Instant nextRun = maybeNextRun.get();
                    if (!nextRun.isAfter(_clock.instant())) {
                        final ActorRef sender = getSender();
                        PatternsCS.pipe(
                                job.execute(getSelf(), nextRun)
                                        .handle((result, error) -> {
                                            if (error == null) {
                                                _jobRef.jobSucceeded(nextRun, result);
                                                return new Success<>(_jobRef, nextRun, result);
                                            } else {
                                                _jobRef.jobFailed(nextRun, error);
                                                return new Failure<>(_jobRef, nextRun, error);
                                            }
                                        }),
                                getContext().dispatcher())
                                .to(sender);
                        _jobRef.jobStarted(nextRun);
                    }
                })
                .build();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JobScheduler.class);

    /**
     * Internal message, telling the scheduler to run any necessary jobs.
     */
    protected static final class Tick {
        public static final Tick INSTANCE = new Tick();
    }

    /**
     *
     * @param <T>
     */
    public static final class Success<T> implements Serializable {
        private final JobRef<T> _jobRef;
        private final Instant _scheduled;
        private final T _result;

        Success(final JobRef<T> jobRef, final Instant scheduled, final T result) {
            _jobRef = jobRef;
            _scheduled = scheduled;
            _result = result;
        }

        public JobRef<T> getJobRef() {
            return _jobRef;
        }

        public Instant getScheduled() {
            return _scheduled;
        }

        public T getResult() {
            return _result;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * @param <T>
     */
    public static final class Failure<T> implements Serializable {
        private final JobRef<T> _jobRef;
        private final Instant _scheduled;
        private final Throwable _error;

        Failure(final JobRef<T> jobRef, final Instant scheduled, final Throwable error) {
            _jobRef = jobRef;
            _scheduled = scheduled;
            _error = error;
        }

        public JobRef<T> getJobRef() {
            return _jobRef;
        }

        public Instant getScheduled() {
            return _scheduled;
        }

        public Throwable getError() {
            return _error;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Message to ask the JobScheduler for its {@link JobRef}.
     */
    public static final class GetJobRef implements Serializable {
        private static final GetJobRef INSTANCE = new GetJobRef();
        public GetJobRef getInstance() {
            return INSTANCE;
        }
        private GetJobRef() {}

        private static final long serialVersionUID = 1L;
    }
}
