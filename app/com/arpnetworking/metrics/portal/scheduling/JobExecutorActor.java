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
import akka.pattern.PatternsCS;
import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.incubator.impl.TsdPeriodicMetrics;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.base.MoreObjects;
import com.google.inject.Injector;
import models.internal.scheduling.Job;
import net.sf.oval.constraint.NotNull;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * An actor that executes {@link Job}s.
 *
 * <p>Lifecycle:</p>
 * <ul>
 *     <li>
 *          <p><b>Unininitialized.</b> The actor is completely passive.</p>
 *          <p>It starts out in this state (after instantiation, or after dying and being restarted).</p>
 *          <p>It leaves this state when it receives a {@link Reload} message.</p>
 *     </li>
 *     <li>
 *         <p><b>Initialized.</b> The actor will intermittently wake up to execute / reload its {@link CachedJob}.</p>
 *         <p>It enters this state when it receives a {@link Reload} message.</p>
 *         <p>It never leaves this state (except when it dies and is resurrected).</p>
 *         <p>Once the actor is initialized, all subsequent {@link Reload} messages <i>must</i> reference the same {@link JobRef}.
 *            Failure to respect this is considered a severe enough programming error that the actor will kill itself.</p>
 *     </li>
 * </ul>
 *
 * @param <T> The type of result produced by the {@link Job}s.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class JobExecutorActor<T> extends AbstractActorWithTimers {

    private final Injector _injector;
    private final Clock _clock;
    private final PeriodicMetrics _periodicMetrics;
    private boolean _currentlyExecuting = false;
    private Optional<CachedJob<T>> _cachedJob = Optional.empty();

    /**
     * Props factory.
     *
     * @param injector The Guice injector to use to load the {@link JobRepository} referenced by the {@link JobRef}.
     * @param clock The clock the scheduler will use, when it ticks, to determine whether it's time to run the next job(s) yet.
     * @return A new props to create this actor.
     */
    public static Props props(
            final Injector injector,
            final Clock clock) {
        return Props.create(JobExecutorActor.class, () -> new JobExecutorActor<>(injector, clock));
    }

    private JobExecutorActor(
            final Injector injector,
            final Clock clock) {
        _injector = injector;
        _clock = clock;
        _periodicMetrics = new TsdPeriodicMetrics.Builder()
                .setMetricsFactory(injector.getInstance(MetricsFactory.class))
                .build();
    }

    @Override
    public void postRestart(final Throwable reason) throws Exception {
        super.postRestart(reason);
        LOGGER.info()
                .setMessage("restarting after error")
                .setThrowable(reason)
                .log();
    }

    private void scheduleTickFor(final Instant wakeUpAt) {
        final FiniteDuration delta = Duration.fromNanos(Math.max(0, ChronoUnit.NANOS.between(_clock.instant(), wakeUpAt)));
        timers().startSingleTimer(TICK_TIMER_NAME, Tick.INSTANCE, delta);
    }

    private void killSelf() {
        // TODO(spencerpearson): Per https://doc.akka.io/docs/akka/2.5.4/java/cluster-sharding.html#remembering-entities ,
        //   might we need to send a Passivate message to our parent?
        //   Relevant: https://github.com/ArpNetworking/metrics-portal/pull/150#discussion_r251037350
        getSelf().tell(PoisonPill.getInstance(), getSelf());
    }

    /**
     * Initializes the actor with the given JobRef (if uninitialized), or ensure the the given ref equals the one already initialized with.
     *
     * @param ref The JobRef to initialize with.
     * @return The {@link CachedJob} the actor is initialized to.
     *   (Guaranteed to equal {@code _cachedJob.get()}; this return value is purely for the typechecker's sake.)
     * @throws IllegalStateException If the actor was already initialized with a different JobRef.
     */
    private CachedJob<T> initializeOrEnsureRefMatch(final JobRef<T> ref) throws IllegalStateException {
        if (!_cachedJob.isPresent()) {
            LOGGER.info()
                    .setMessage("initializing")
                    .addData("ref", ref)
                    .log();
            _cachedJob = Optional.of(new CachedJob<>(_injector, ref, _periodicMetrics));
        }

        final JobRef<T> oldRef = _cachedJob.get().getRef();
        if (!oldRef.equals(ref)) {
            killSelf();
            throw new IllegalStateException(String.format("got JobRef %s, but already initialized with %s", ref, oldRef));
        }

        return _cachedJob.get();
    }


    private JobRef<T> unsafeJobRefCast(@SuppressWarnings("rawtypes") final JobRef ref) {
        // THIS MAKES ME SO SAD. But there's simply no way to plumb the type information through Akka.
        @SuppressWarnings("unchecked")
        final JobRef<T> typedRef = ref;
        return typedRef;
    }

    private void attemptExecuteAndUpdateRepository(final Instant scheduled) {
        if (!_cachedJob.isPresent()) {
            killSelf();
            throw new IllegalStateException("unable to execute: executor is not initialized");
        }

        final CachedJob<T> cachedJob = _cachedJob.get();

        final JobRef<T> ref = cachedJob.getRef();
        final Job<T> job = cachedJob.getJob();
        final JobRepository<T> repo = ref.getRepository(_injector);

        if (_currentlyExecuting) {
            return;
        }
        _currentlyExecuting = true;

        try {
            repo.jobStarted(ref.getJobId(), ref.getOrganization(), scheduled);
        } catch (final NoSuchElementException error) {
            _currentlyExecuting = false;
            killSelf();
            LOGGER.warn()
                    .setMessage("attempted to start executing job, but job no longer exists in repository")
                    .addData("ref", cachedJob.getRef())
                    .addData("scheduled", scheduled)
                    .log();
            return;
        }

        PatternsCS.pipe(
                job.execute(getSelf(), scheduled)
                        .handle((result, error) -> new JobCompleted.Builder<T>()
                                    .setScheduled(scheduled)
                                    .setOutcome(error, result)
                                    .build()),
                getContext().system().dispatcher()
        ).to(getSelf());
    }

    private void tick(final Tick message) {
        _periodicMetrics.recordCounter("job_executor_actor_ticks", 1);

        if (!_cachedJob.isPresent()) {
            killSelf();
            throw new IllegalStateException("somehow, uninitialized JobExecutorActor is trying to tick");
        }
        final CachedJob<T> cachedJob = _cachedJob.get();

        final Optional<Instant> nextRun = cachedJob.getSchedule().nextRun(cachedJob.getLastRun());
        if (!nextRun.isPresent()) {
            LOGGER.info()
                    .setMessage("job has no more scheduled runs")
                    .addData("cachedJob", cachedJob)
                    .log();
            killSelf();
            return;
        }

        if (_clock.instant().isBefore(nextRun.get().minus(EXECUTION_SLOP))) {
            scheduleTickFor(nextRun.get());
        } else {
            attemptExecuteAndUpdateRepository(nextRun.get());
        }
    }

    private void reload(final Reload<T> message) {
        final Optional<String> eTag = message.getETag();
        final CachedJob<T> cachedJob;
        _periodicMetrics.recordCounter("job_executor_actor_reloads", 1);
        try {
            cachedJob = initializeOrEnsureRefMatch(unsafeJobRefCast(message.getJobRef()));
            if (eTag.isPresent()) {
                cachedJob.reloadIfOutdated(_injector, eTag.get());
            } else {
                cachedJob.reload(_injector);
            }
        } catch (final NoSuchElementException error) {
            LOGGER.warn()
                    .setMessage("tried to reload job, but job no longer exists in repository")
                    .addData("ref", message.getJobRef())
                    .log();
            killSelf();
            return;
        }
        getSelf().tell(Tick.INSTANCE, getSelf());
    }

    private void jobCompleted(final JobCompleted<?> message) {
        _currentlyExecuting = false;
        if (!_cachedJob.isPresent()) {
            LOGGER.error()
                    .setMessage("uninitialized, but got completion message (perhaps from previous life?)")
                    .addData("scheduled", message.getScheduled())
                    .addData("outcome", message.getOutcome())
                    .log();
            return;
        }
        final CachedJob<T> cachedJob = _cachedJob.get();
        final JobRef<T> ref = cachedJob.getRef();

        @SuppressWarnings("unchecked")
        final JobCompleted<T> typedMessage = (JobCompleted<T>) message;
        final JobRepository<T> repo = ref.getRepository(_injector);
        try {
            _periodicMetrics.recordCounter(
                    "job_executor_actor_execution_successes",
                    message.getOutcome().isRight() ? 1 : 0);
            if (message.getOutcome().isLeft()) {
                LOGGER.debug()
                        .setMessage("marking job as failed")
                        .addData("ref", ref)
                        .addData("scheduled", message.getScheduled())
                        .addData("error", message.getOutcome().left().get())
                        .log();
                repo.jobFailed(
                        ref.getJobId(),
                        ref.getOrganization(),
                        message.getScheduled(),
                        typedMessage.getOutcome().left().get());
            } else {
                LOGGER.debug()
                        .setMessage("marking job as successful")
                        .addData("ref", ref)
                        .addData("scheduled", message.getScheduled())
                        .log();
                repo.jobSucceeded(
                        ref.getJobId(),
                        ref.getOrganization(),
                        message.getScheduled(),
                        typedMessage.getOutcome().right().get());
            }
            cachedJob.reload(_injector);
        } catch (final NoSuchElementException error) {
            LOGGER.warn()
                    .setMessage("tried to job as complete, but job no longer exists in repository")
                    .addData("ref", ref)
                    .addData("scheduled", message.getScheduled())
                    .log();
            killSelf();
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Tick.class, this::tick)
                .match(Reload.class, message -> {
                    @SuppressWarnings("unchecked")
                    final Reload<T> typedMessage = (Reload<T>) message;
                    this.reload(typedMessage);
                })
                .match(JobCompleted.class, message -> {
                    @SuppressWarnings("unchecked")
                    final JobCompleted<T> typedMessage = (JobCompleted<T>) message;
                    this.jobCompleted(typedMessage);
                })
                .build();
    }

    private static final String TICK_TIMER_NAME = "TICK";
    /**
     * If we wake up very slightly before we're supposed to execute, we should just execute,
     * rather than scheduling another wakeup in the very near future.
     */
    private static final java.time.Duration EXECUTION_SLOP = java.time.Duration.ofMillis(500);
    private static final Logger LOGGER = LoggerFactory.getLogger(JobExecutorActor.class);

    /**
     * Internal message, telling the scheduler to run any necessary jobs.
     * Intended only for internal use and testing.
     */
    /* package private */ static final class Tick implements Serializable {
        /* package private */ static final Tick INSTANCE = new Tick();
        private static final long serialVersionUID = 1L;
    }

    /**
     * Commands the actor to reload its job from its repository, if the cached ETag is out of date.
     *
     * @param <T> The type of the result computed by the referenced {@link Job}.
     */
    public static final class Reload<T> implements Serializable {
        private final JobRef<T> _jobRef;
        private final String _eTag;

        private Reload(final Builder<T> builder) {
            _jobRef = builder._jobRef;
            _eTag = builder._eTag;
        }

        public JobRef<T> getJobRef() {
            return _jobRef;
        }

        public Optional<String> getETag() {
            return Optional.ofNullable(_eTag);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Reload<?> reload = (Reload<?>) o;
            return _jobRef.equals(reload._jobRef)
                    && Objects.equals(_eTag, reload._eTag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_jobRef, _eTag);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("ref", _jobRef)
                    .add("eTag", _eTag)
                    .toString();
        }

        private static final long serialVersionUID = 1L;

        /**
         * Implementation of builder pattern for {@link Reload}.
         *
         * @param <T> The type of the result computed by the referenced {@link Job}.
         *
         * @author Spencer Pearson (spencerpearson at dropbox dot com)
         */
        public static final class Builder<T> extends OvalBuilder<Reload<T>> {
            @NotNull
            private JobRef<T> _jobRef;
            private String _eTag;

            /**
             * Public constructor.
             */
            Builder() {
                super(Reload<T>::new);
            }

            /**
             * The {@link JobRef} to load. Required. Must not be null.
             *
             * @param jobRef The reference to the job whose actor to contact.
             * @return This instance of Builder.
             */
            public Builder<T> setJobRef(final JobRef<T> jobRef) {
                _jobRef = jobRef;
                return this;
            }

            /**
             * Causes the actor not to reload if it equals the actor's current ETag. Optional. Defaults to null.
             *
             * @param eTag The ETag. Null means "unconditional reload."
             * @return This instance of Builder.
             */
            public Builder<T> setETag(@Nullable final String eTag) {
                _eTag = eTag;
                return this;
            }
        }
    }

    /**
     * Indicates that a job completed (either successfully or unsuccessfully).
     *
     * @param <T> The type of the result computed by the referenced {@link Job}.
     */
    public static final class JobCompleted<T> {
        private final Instant _scheduled;
        private final Either<Throwable, T> _outcome;

        private JobCompleted(final Builder<T> builder) {
            _scheduled = builder._scheduled;
            _outcome = builder._outcome;
        }

        public Instant getScheduled() {
            return _scheduled;
        }

        public Either<Throwable, T> getOutcome() {
            return _outcome;
        }

        /**
         * Implementation of builder pattern for {@link JobCompleted}.
         *
         * @param <T> The type of the result computed by the referenced {@link Job}.
         *
         * @author Spencer Pearson (spencerpearson at dropbox dot com)
         */
        public static final class Builder<T> extends OvalBuilder<JobCompleted<T>> {
            @NotNull
            private Instant _scheduled;
            @NotNull
            private Either<Throwable, T> _outcome;

            /**
             * Public constructor.
             */
            Builder() {
                super(JobCompleted<T>::new);
            }

            /**
             * The instant the job-run was scheduled for. Required. Must not be null.
             *
             * @param scheduled The instant.
             * @return This instance of Builder.
             */
            public Builder<T> setScheduled(final Instant scheduled) {
                _scheduled = scheduled;
                return this;
            }

            /**
             * Convenience function to delegate to either {@code setResult} or {@code setError} as appropriate.
             *
             * Either {@code result} or {@code error} must be non-null.
             *
             * @param result The result (or null).
             * @param error The error which caused the job to fail (or null).
             *
             * @return This instance of Builder.
             */
            public Builder<T> setOutcome(@Nullable final Throwable error, @Nullable final T result) {
                if (error == null) {
                    if (result == null) {
                        throw new IllegalArgumentException("result and error can't both be null");
                    }
                    _outcome = new Right<>(result);
                } else {
                    _outcome = new Left<>(error);
                }
                return this;
            }
        }
    }

}
