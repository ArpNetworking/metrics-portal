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
import com.arpnetworking.metrics.Units;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.base.CaseFormat;
import com.google.common.base.MoreObjects;
import com.google.inject.Injector;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import models.internal.scheduling.Job;
import net.sf.oval.constraint.NotNull;
import net.sf.oval.constraint.ValidateWithMethod;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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
     * @param periodicMetrics The {@link PeriodicMetrics} that this actor will use to log its metrics.
     * @return A new props to create this actor.
     */
    public static Props props(final Injector injector, final Clock clock, final PeriodicMetrics periodicMetrics) {
        return Props.create(JobExecutorActor.class, () -> new JobExecutorActor<>(injector, clock, periodicMetrics));
    }

    private JobExecutorActor(final Injector injector, final Clock clock, final PeriodicMetrics periodicMetrics) {
        _injector = injector;
        _clock = clock;
        _periodicMetrics = periodicMetrics;
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
        timers().startSingleTimer(EXTRA_TICK_TIMER_NAME, Tick.INSTANCE, delta);
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
     * @throws NoSuchJobException If the actor attempts to initialize itself but can't load the referenced job.
     */
    private CachedJob<T> initializeOrEnsureRefMatch(final JobRef<T> ref) throws IllegalStateException, NoSuchJobException {
        if (!_cachedJob.isPresent()) {
            LOGGER.info()
                    .setMessage("initializing")
                    .addData("ref", ref)
                    .log();
            _cachedJob = Optional.of(CachedJob.createAndLoad(_injector, ref, _periodicMetrics));
        }

        final JobRef<T> oldRef = _cachedJob.get().getRef();
        if (!oldRef.equals(ref)) {
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

    /**
     * Executes the job, notifying the repository upon start and completion. (Unless an execution is already in progress; then, noop.)
     * (Well, technically, sends the actor a message upon completion, and <i>that</i> notifies the repository about success/failure.)
     *
     * @param scheduled The time that the job was scheduled for.
     * @throws ActorNotInitializedException If the actor has never been given a {@link JobRef}, and therefore has nothing to execute.
     * @throws NoSuchJobException If the repository doesn't recognize the job.
     */
    private void attemptExecuteAndUpdateRepository(final Instant scheduled) throws ActorNotInitializedException, NoSuchJobException {
        if (!_cachedJob.isPresent()) {
            throw new ActorNotInitializedException("unable to execute: executor is not initialized");
        }

        final CachedJob<T> cachedJob = _cachedJob.get();

        final JobRef<T> ref = cachedJob.getRef();
        final Job<T> job = cachedJob.getJob();
        final JobExecutionRepository<T> repo = ref.getExecutionRepository(_injector);

        if (_currentlyExecuting) {
            return;
        }
        _currentlyExecuting = true;

        try {
            repo.jobStarted(ref.getJobId(), ref.getOrganization(), scheduled);
        } catch (final NoSuchElementException error) {
            _currentlyExecuting = false;
            throw new NoSuchJobException("job no longer exists in repository", error);
        }

        final long startTime = System.nanoTime();
        PatternsCS.pipe(
                job.execute(_injector, scheduled)
                        .handle((result, error) -> {
                            _periodicMetrics.recordTimer(
                                    "jobs/executor/execution_time",
                                    System.nanoTime() - startTime,
                                    Optional.of(Units.NANOSECOND));

                            _periodicMetrics.recordTimer(
                                    "jobs/executor/by_type/"
                                            + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, job.getClass().getSimpleName())
                                            + "/execution_time",
                                    System.nanoTime() - startTime,
                                    Optional.of(Units.NANOSECOND));

                            return new JobCompleted.Builder<T>()
                                    .setScheduled(scheduled)
                                    .setError(error)
                                    .setResult(result)
                                    .build();
                        }),
                getContext().dispatcher()
        ).to(getSelf());
    }

    /**
     * Wakes up the actor and causes it run the job or schedule another tick, as appropriate.
     *
     * If the job's next scheduled execution is before ~now, execute it.
     * Otherwise, schedule a tick for the next execution time.
     *
     * @param message A {@link Tick} message.
     * @throws ActorNotInitializedException If the actor (somehow) receives this message before getting a handle to a {@link JobRef}.
     */
    private void tick(final Tick message) throws ActorNotInitializedException {
        _periodicMetrics.recordCounter("jobs/executor/tick", 1);

        if (!_cachedJob.isPresent()) {
            throw new ActorNotInitializedException("somehow, uninitialized JobExecutorActor is trying to tick");
        }
        final CachedJob<T> cachedJob = _cachedJob.get();

        _periodicMetrics.recordCounter(
                "jobs/executor/by_type/"
                        + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, cachedJob.getJob().getClass().getSimpleName())
                        + "/tick",
                1);

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
            try {
                attemptExecuteAndUpdateRepository(nextRun.get());
            } catch (final NoSuchJobException error) {
                LOGGER.warn()
                        .setMessage("attempted to start executing job, but job no longer exists in repository")
                        .addData("ref", cachedJob.getRef())
                        .addData("scheduled", nextRun.get())
                        .log();
                killSelf();
            }
        }
    }

    private void reload(final Reload<T> message) {
        final Optional<String> eTag = message.getETag();
        final CachedJob<T> cachedJob;
        _periodicMetrics.recordCounter("jobs/executor/reload", 1);
        try {
            cachedJob = initializeOrEnsureRefMatch(unsafeJobRefCast(message.getJobRef()));
            if (eTag.isPresent()) {
                cachedJob.reloadIfOutdated(_injector, eTag.get());
            } else {
                cachedJob.reload(_injector);
            }
            _periodicMetrics.recordCounter(
                    "jobs/executor/by_type/"
                            + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, cachedJob.getJob().getClass().getSimpleName())
                            + "/reload",
                    1);
        } catch (final NoSuchJobException error) {
            LOGGER.warn()
                    .setMessage("tried to reload job, but job no longer exists in repository")
                    .addData("ref", message.getJobRef())
                    .log();
            killSelf();
            return;
        }
        timers().startPeriodicTimer(PERIODIC_TICK_TIMER_NAME, Tick.INSTANCE, TICK_INTERVAL);
        getSelf().tell(Tick.INSTANCE, getSelf());
    }

    private void jobCompleted(final JobCompleted<?> message) {
        _currentlyExecuting = false;
        if (!_cachedJob.isPresent()) {
            LOGGER.warn()
                    .setMessage("uninitialized, but got completion message (perhaps from previous life?)")
                    .addData("scheduled", message.getScheduled())
                    .addData("error", message.getError())
                    .log();
            return;
        }
        final CachedJob<T> cachedJob = _cachedJob.get();
        final JobRef<T> ref = cachedJob.getRef();

        @SuppressWarnings("unchecked")
        final JobCompleted<T> typedMessage = (JobCompleted<T>) message;
        final JobExecutionRepository<T> repo = ref.getExecutionRepository(_injector);
        try {
            final int successMetricValue = message.getError() == null ? 1 : 0;
            _periodicMetrics.recordCounter(
                    "jobs/executor/execution_success",
                    successMetricValue);
            _periodicMetrics.recordCounter(
                    "jobs/executor/by_type/"
                    + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, cachedJob.getJob().getClass().getSimpleName())
                    + "/execution_success",
                    successMetricValue);
            if (message.getError() == null) {
                if (typedMessage.getResult() == null) {
                    throw new IllegalArgumentException(String.format("JobCompleted message for %s has null error *and* result", ref));
                }
                LOGGER.info()
                        .setMessage("marking job as successful")
                        .addData("ref", ref)
                        .addData("scheduled", message.getScheduled())
                        .log();
                repo.jobSucceeded(
                        ref.getJobId(),
                        ref.getOrganization(),
                        message.getScheduled(),
                        typedMessage.getResult());
            } else {
                LOGGER.error()
                        .setMessage("marking job as failed")
                        .addData("ref", ref)
                        .addData("scheduled", message.getScheduled())
                        .setThrowable(message.getError())
                        .log();
                repo.jobFailed(
                        ref.getJobId(),
                        ref.getOrganization(),
                        message.getScheduled(),
                        typedMessage.getError());
            }
        } catch (final NoSuchElementException error) {
            LOGGER.warn()
                    .setMessage("tried to job as complete, but job no longer exists in repository")
                    .addData("ref", ref)
                    .addData("scheduled", message.getScheduled())
                    .log();
            killSelf();
            return;
        }

        getSelf().tell(new Reload.Builder<T>().setJobRef(ref).build(), getSelf());
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

    private static final String EXTRA_TICK_TIMER_NAME = "EXTRA_TICK";
    private static final String PERIODIC_TICK_TIMER_NAME = "PERIODIC_TICK";
    private static final FiniteDuration TICK_INTERVAL = Duration.apply(1, TimeUnit.MINUTES);
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
            public Builder() {
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
     * Indicates that a job completed (either successfully, if {@code getError()==null}, or unsuccessfully, otherwise).
     *
     * @param <T> The type of the result computed by the referenced {@link Job}.
     */
    private static final class JobCompleted<T> {
        private final Instant _scheduled;
        @Nullable
        private final Throwable _error;
        @Nullable
        private final T _result;

        private JobCompleted(final Builder<T> builder) {
            _scheduled = builder._scheduled;
            _error = builder._error;
            _result = builder._result;
        }

        public Instant getScheduled() {
            return _scheduled;
        }

        @Nullable
        public Throwable getError() {
            return _error;
        }

        @Nullable
        public T getResult() {
            return _result;
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
            private Throwable _error;
            @ValidateWithMethod(methodName = "validateErrorAndResult", parameterType = Object.class)
            private T _result;

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
             * The error (if any) that caused the job to fail. Optional. Defaults to null.
             * The job is considered to have succeeded if (and only if) this field is null.
             *
             * @param error The error.
             * @return This instance of Builder.
             */
            public Builder<T> setError(@Nullable final Throwable error) {
                _error = error;
                return this;
            }

            /**
             * The result that the job computed. Optional. Defaults to null.
             *
             * @param result The result.
             * @return This instance of Builder.
             */
            public Builder<T> setResult(@Nullable final T result) {
                _result = result;
                return this;
            }

            @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "invoked reflectively by @ValidateWithMethod")
            private boolean validateErrorAndResult(@Nullable final Object result) {
                return (result == null) ^ (_error == null);
            }
        }
    }

    private static final class ActorNotInitializedException extends Exception {
        private ActorNotInitializedException(final String message) {
            super(message);
        }
        private static final long serialVersionUID = 1L;
    }

}
