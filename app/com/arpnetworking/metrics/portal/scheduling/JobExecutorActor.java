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

import org.apache.pekko.actor.AbstractActorWithTimers;
import org.apache.pekko.actor.PoisonPill;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.Status;
import org.apache.pekko.cluster.sharding.ShardRegion;
import org.apache.pekko.pattern.Patterns;
import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.notcommons.serialization.DeserializationException;
import com.arpnetworking.notcommons.serialization.Deserializer;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.base.CaseFormat;
import com.google.common.base.MoreObjects;
import com.google.inject.Injector;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import models.internal.scheduling.Job;
import models.internal.scheduling.JobExecution;
import net.sf.oval.constraint.NotNull;
import net.sf.oval.constraint.ValidateWithMethod;

import java.io.Serializable;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
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
 *         <p><b>Initialized.</b> The actor will intermittently wake up to execute / reload its {@link Job}.</p>
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
    /**
     * high-level actor state diagram.
     *
     * arrows are messages that transition between states.
     * boxes are internal states of execution.
     *
     * We assume the following invariants wrt reloads:
     * - If multiple Reload messages occur, ones received while in the process
     *   of reloading or executing are ignored.
     * - If multiple Tick messages occur, ones received while in the process of
     *   executing are ignored.
     * - Execution completion always triggers a reload, even if we would otherwise tick.
     *
     *     +---------+  reload  +----------+  reload  +--------------+
     *     |  actor  +--------->+  reload  +<---------+  antientropy |
     *     |  start  |          |   job    |          |     tick     |
     *     +---------+          +----------+          +--------------+
     *                          |          ^
     *               restart    v          |   execution complete          ^ Uninitialized  ^
     *         +----<------<----+          +-------<-------<-------        ------------------
     *         |     ticker                                       ^        v  Initialized   v
     *         |                                                  |
     *         v                                                  |
     * +-------------+         +--------------+   job       +-----+--------+
     * | Initialized |  tick   |      job     |  complete   |  record job  | error
     * |  (ticking)  +-------->+   executing  +------------>+   results    +-------> crash / restart
     * +-------------+         +--------------+             +--------------+
     */

    private final Injector _injector;
    private final Clock _clock;
    private final PeriodicMetrics _periodicMetrics;

    /**
     * Flag to ensure that we only ever execute one job at a time.
     * Once a tick is received, this flag will not be reset until another reload occurs.
     */
    private boolean _currentlyExecuting = false;
    private boolean _currentlyReloading = false;

    private Optional<JobRef<T>> _ref = Optional.empty();
    private Optional<Job<T>> _cachedJob = Optional.empty();
    private Optional<Instant> _lastRun = Optional.empty();

    private Optional<Instant> _nextRun = Optional.empty();
    private final Deserializer<JobRef<?>> _refDeserializer;

    private JobExecutorActor(
            final Injector injector,
            final Clock clock,
            final PeriodicMetrics periodicMetrics,
            final Deserializer<JobRef<?>> refDeserializer
    ) {
        _injector = injector;
        _clock = clock;
        _periodicMetrics = periodicMetrics;
        _refDeserializer = refDeserializer;
    }

    /**
     * Props factory.
     *
     * @param injector The Guice injector to use to load the {@link JobRepository} referenced by the {@link JobRef}.
     * @param clock The clock the scheduler will use, when it ticks, to determine whether it's time to run the next job(s) yet.
     * @param periodicMetrics The {@link PeriodicMetrics} that this actor will use to log its metrics.
     * @param refDeserializer The JobRefSerializer that this actor will use reconstruct its JobRef at startup.
     * @return A new props to create this actor.
     */
    public static Props props(
            final Injector injector,
            final Clock clock,
            final PeriodicMetrics periodicMetrics,
            final Deserializer<JobRef<?>> refDeserializer
    ) {
        return Props.create(JobExecutorActor.class,
                () -> new JobExecutorActor<>(injector, clock, periodicMetrics, refDeserializer));
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();

        final String actorName = getSelf().path().name();
        try {
            final String decodedActorName = URLDecoder.decode(actorName, StandardCharsets.UTF_8.name());
            final JobRef<T> ref = unsafeJobRefCast(_refDeserializer.deserialize(decodedActorName));
            LOGGER.info()
                    .setMessage("inferred job ref from name, triggering reload")
                    .addData("jobRef", ref.toString())
                    .log();
            getSelf().tell(new Reload.Builder<T>().setJobRef(ref).build(), getSelf());
        } catch (final DeserializationException e) {
            LOGGER.warn()
                    .setMessage("could not infer job ref from name, the actor could have been started incorrectly.")
                    .setThrowable(e)
                    .addData("actorName", actorName)
                    .log();
            killSelfPermanently();
        }
    }

    @Override
    public void postRestart(final Throwable reason) throws Exception {
        super.postRestart(reason);
        LOGGER.info()
                .setMessage("restarting after error")
                .addData("actorRef", self())
                .setThrowable(reason)
                .log();
    }

    private void scheduleTickFor(final Instant wakeUpAt) {
        final Duration delta = Duration.ofNanos(Math.max(0, ChronoUnit.NANOS.between(_clock.instant(), wakeUpAt)));
        timers().startSingleTimer(EXTRA_TICK_TIMER_NAME, Tick.INSTANCE, delta);
    }

    private void killSelf() {
        self().tell(PoisonPill.getInstance(), getSelf());
    }

    private void killSelfPermanently() {
        getContext().getParent().tell(new ShardRegion.Passivate(PoisonPill.getInstance()), getSelf());
    }

    /**
     * Initializes the actor with the given JobRef (if uninitialized), or ensure the the given ref equals the one already initialized with.
     *
     * @param ref The JobRef to initialize with.
     * @throws IllegalStateException If the actor was already initialized with a different JobRef.
     * @throws NoSuchJobException If the actor attempts to initialize itself but can't load the referenced job.
     */
    private void initializeOrEnsureRefMatch(final JobRef<T> ref) throws IllegalStateException, NoSuchJobException {
        if (_ref.isPresent() && !ref.equals(_ref.get())) {
            LOGGER.error().setMessage("refs no longer match").log();
            killSelfPermanently();
        }
        _ref = Optional.of(ref);

        LOGGER.info()
                .setMessage("initializing")
                .addData("actorRef", self())
                .addData("ref", ref)
                .log();

        final Optional<Job<T>> loaded = ref.get(_injector);
        if (!loaded.isPresent()) {
            _periodicMetrics.recordCounter("cached_job_reload_success", 0);
            throw new NoSuchJobException(ref.toString());
        }
        _periodicMetrics.recordCounter("cached_job_reload_success", 1);
        _cachedJob = loaded;
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
     */
    private void attemptExecuteAndUpdateRepository(final Instant scheduled) throws ActorNotInitializedException {
        if (!_cachedJob.isPresent() || !_ref.isPresent()) {
            throw new ActorNotInitializedException("unable to execute: executor is not initialized");
        }

        final JobRef<T> ref = _ref.get();
        final Job<T> job = _cachedJob.get();
        final JobExecutionRepository<T> repo = ref.getExecutionRepository(_injector);

        if (_currentlyExecuting) {
            LOGGER.debug()
                .setMessage("ignoring extra tick received mid-execution")
                .addData("job", job)
                .addData("ref", ref)
                .addData("lastRun", _lastRun)
                .addData("actorRef", self())
                .log();
            return;
        }
        _currentlyExecuting = true;

        final CompletionStage<Object> executionFut = repo.jobStarted(ref.getJobId(), ref.getOrganization(), scheduled)
                .thenCompose(ignored -> {
                    // Ideally we could use the same start time defined below instead of
                    // Instant.now but because System.nanoTime does not return wall-clock
                    // time it can't be compared to an Instant.
                    final long executionLagNanos = ChronoUnit.NANOS.between(scheduled, Instant.now());
                    _periodicMetrics.recordTimer(
                            "jobs/executor/execution_lag",
                            executionLagNanos,
                            Optional.of(TimeUnit.NANOSECONDS));

                    _periodicMetrics.recordTimer(
                            "jobs/executor/by_type/"
                                    + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, job.getClass().getSimpleName())
                                    + "/execution_lag",
                            executionLagNanos,
                            Optional.of(TimeUnit.NANOSECONDS));

                    final long startTime = System.nanoTime();
                    return job.execute(_injector, scheduled).handle((result, error) -> {
                        _periodicMetrics.recordTimer(
                                "jobs/executor/execution_time",
                                System.nanoTime() - startTime,
                                Optional.of(TimeUnit.NANOSECONDS));

                        _periodicMetrics.recordTimer(
                                "jobs/executor/by_type/"
                                        + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, job.getClass().getSimpleName())
                                        + "/execution_time",
                                System.nanoTime() - startTime,
                                Optional.of(TimeUnit.NANOSECONDS));
                        return new JobCompleted.Builder<T>()
                                .setScheduled(scheduled)
                                .setError(error)
                                .setResult(result)
                                .build();
                    }).handle((result, error) -> {
                        if (error == null) {
                            return result;
                        }
                        if (error instanceof NoSuchElementException) {
                            LOGGER.warn()
                                    .setMessage("attempted to execute job, but job no longer exists in repository")
                                    .addData("ref", ref)
                                    .addData("scheduled", scheduled)
                                    .log();
                            return REQUEST_PERMANENT_SHUTDOWN;
                        }
                        // Propagate any other error to restart the actor
                        throw new CompletionException(error);
                    });
                });

        Patterns.pipe(
                executionFut,
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
        _periodicMetrics.recordCounter(
                "jobs/executor/by_type/"
                        + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, _cachedJob.get().getClass().getSimpleName())
                        + "/tick",
                1);

        if (!_nextRun.isPresent()) {
            _nextRun = _cachedJob.get().getSchedule().nextRun(_lastRun);
        }
        if (!_nextRun.isPresent()) {
            LOGGER.info()
                    .setMessage("job has no more scheduled runs")
                    .addData("job", _cachedJob)
                    .addData("ref", _ref)
                    .addData("lastRun", _lastRun)
                    .addData("actorRef", self())
                    .log();
            killSelfPermanently();
            return;
        }

        if (_clock.instant().isBefore(_nextRun.get().minus(EXECUTION_SLOP))) {
            scheduleTickFor(_nextRun.get());
        } else {
            attemptExecuteAndUpdateRepository(_nextRun.get());
        }
    }

    private void reload(final Reload<T> message) {
        if (_currentlyExecuting || _currentlyReloading) {
            final String reason = _currentlyExecuting ? "already executing" : "already reloading";
            LOGGER.debug()
                    .setMessage("ignoring extra reload message")
                    .addData("jobRef", message.getJobRef())
                    .addData("ignoreReason", reason)
                    .addData("lastRun", _lastRun)
                    .addData("actorRef", self())
                    .log();
            return;
        }
        final Optional<String> eTag = message.getETag();
        final boolean needsUpdate = _cachedJob
                .flatMap(Job::getETag)
                .flatMap(currentTag -> eTag.map(e -> !e.equals(currentTag)))
                .orElse(true);

        _periodicMetrics.recordCounter("cached_job_conditional_reload_necessary", needsUpdate ? 1 : 0);
        if (!needsUpdate && _lastRun.isPresent()) {
            self().tell(new RestartTicker(_lastRun), self());
            return;
        }
        _currentlyReloading = true;
        LOGGER.debug()
                .setMessage("reloading job")
                .addData("jobRef", _ref)
                .addData("oldETag", _cachedJob.flatMap(Job::getETag))
                .addData("newETag", eTag)
                .addData("actorRef", self())
                .log();
        _periodicMetrics.recordCounter("jobs/executor/reload", 1);
        final JobRef<T> ref = unsafeJobRefCast(message.getJobRef());
        try {
            initializeOrEnsureRefMatch(ref);
            _periodicMetrics.recordCounter(
                    "jobs/executor/by_type/"
                            + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, _cachedJob.get().getClass().getSimpleName())
                            + "/reload",
                    1);
        } catch (final NoSuchJobException error) {
            LOGGER.warn()
                    .setMessage("tried to reload job, but job no longer exists in repository")
                    .addData("ref", ref)
                    .log();
            killSelfPermanently();
            return;
        }
        // At this point we can use "ref" since it's guaranteed to be valid, otherwise
        // initialization would have failed above.
        Patterns.pipe(
                ref.getExecutionRepository(_injector)
                        .getLastCompleted(ref.getJobId(), ref.getOrganization())
                        .thenApply(exec -> exec.map(JobExecution::getScheduled))
                        .thenApply(RestartTicker::new),
                getContext().getDispatcher()
        ).to(self());
    }

    private void jobCompleted(final JobCompleted<?> message) {
        if (!_cachedJob.isPresent() || !_ref.isPresent()) {
            LOGGER.warn()
                    .setMessage("uninitialized, but got completion message (perhaps from previous life?)")
                    .addData("scheduled", message.getScheduled())
                    .addData("error", message.getError())
                    .addData("actorRef", self())
                    .log();
            return;
        }
        final JobRef<T> ref = assertInitialized();

        _lastRun = _nextRun;
        _nextRun = Optional.empty();
        @SuppressWarnings("unchecked")
        final JobCompleted<T> typedMessage = (JobCompleted<T>) message;
        final JobExecutionRepository<T> repo = ref.getExecutionRepository(_injector);

        final CompletionStage<Void> updateFut;
        final int successMetricValue = message.getError() == null ? 1 : 0;
        _periodicMetrics.recordCounter(
                "jobs/executor/execution_success",
                successMetricValue);
        _periodicMetrics.recordCounter(
                "jobs/executor/by_type/"
                + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, _cachedJob.get().getClass().getSimpleName())
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
                    .addData("actorRef", self())
                    .log();
            updateFut = repo.jobSucceeded(
                    ref.getJobId(),
                    ref.getOrganization(),
                    message.getScheduled(),
                    typedMessage.getResult()
                ).thenApply(ignore -> null);
        } else {
            LOGGER.error()
                    .setMessage("marking job as failed")
                    .addData("ref", ref)
                    .addData("scheduled", message.getScheduled())
                    .addData("actorRef", self())
                    .setThrowable(message.getError())
                    .log();
            updateFut = repo.jobFailed(
                    ref.getJobId(),
                    ref.getOrganization(),
                    message.getScheduled(),
                    typedMessage.getError());
        }

        handleJobCompletedUpdate(updateFut, message.getScheduled(), message.getError());
    }

    private void handleJobCompletedUpdate(
            final CompletionStage<Void> updateFut,
            final Instant scheduled,
            @Nullable final Throwable jobError
    ) {
        final JobRef<T> ref = assertInitialized();
        Patterns.pipe(
            updateFut.handle((ignored, error) -> {
                if (error == null) {
                    return new ExecutionCompleted<>(new Reload.Builder<T>()
                            .setJobRef(ref)
                            .setETag(_cachedJob.flatMap(Job::getETag).orElse(null))
                            .build());
                }
                if (error instanceof NoSuchElementException) {
                    LOGGER.warn()
                            .setMessage("tried to mark job as complete, but job no longer exists in repository")
                            .addData("ref", ref)
                            .addData("scheduled", scheduled)
                            .addData("actorRef", self())
                            .log();
                    return REQUEST_PERMANENT_SHUTDOWN;
                }
                LOGGER.error()
                        .setMessage("Failed to mark job as complete")
                        .setThrowable(error)
                        .addData("ref", ref)
                        .addData("scheduled", scheduled)
                        .addData("jobError", jobError)
                        .addData("actorRef", self())
                        .log();
                // Propagate the exception.
                //
                // This will indirectly trigger a reload anyway (see JobExecutorActor#preStart).
                //
                // We do this instead of reload directly because our supervisor will rate limit
                // the resurrection of our actor, whereas reloading directly could lead to
                // a tight retry loop if the DB issue is not transient.
                throw new CompletionException(error);
            }).toCompletableFuture(),
            getContext().getDispatcher()
        ).to(self());
    }

    private JobRef<T> assertInitialized() {
        return _ref.orElseThrow(() -> new IllegalStateException("expected ref to be initialized"));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                // Messages that can be sent from outside the actor.
                .match(Reload.class, message -> {
                    @SuppressWarnings("unchecked")
                    final Reload<T> typedMessage = (Reload<T>) message;
                    this.reload(typedMessage);
                })
                // Messages that always originate from inside the actor
                .match(Tick.class, this::tick)
                .match(ExecutionCompleted.class, message -> {
                    _currentlyExecuting = false;
                    // We invoke the reload handler directly instead of forwarding
                    // to avoid racing the next tick.
                    //
                    // i.e. the timer could tick before we could forward our
                    // message which would prevent a reload.
                    @SuppressWarnings("unchecked")
                    final Reload<T> typedMessage = (Reload<T>) message.getReload();
                    this.reload(typedMessage);
                })
                .match(JobCompleted.class, message -> {
                    @SuppressWarnings("unchecked")
                    final JobCompleted<T> typedMessage = (JobCompleted<T>) message;
                    this.jobCompleted(typedMessage);
                })
                .match(RestartTicker.class, message -> {
                    _currentlyReloading = false;
                    _lastRun = message.getLastRun();
                    timers().startTimerAtFixedRate(PERIODIC_TICK_TIMER_NAME, Tick.INSTANCE, TICK_INTERVAL);
                    getSelf().tell(Tick.INSTANCE, getSelf());
                })
                // If any message piping future fails the actor should be restarted.
                .match(Status.Failure.class, message -> killSelf())
                .matchEquals(REQUEST_PERMANENT_SHUTDOWN, message -> killSelfPermanently())
                .build();
    }

    private static final String EXTRA_TICK_TIMER_NAME = "EXTRA_TICK";
    private static final String PERIODIC_TICK_TIMER_NAME = "PERIODIC_TICK";
    private static final Duration TICK_INTERVAL = Duration.ofMinutes(1);
    /**
     * If we wake up very slightly before we're supposed to execute, we should just execute,
     * rather than scheduling another wakeup in the very near future.
     */
    private static final java.time.Duration EXECUTION_SLOP = java.time.Duration.ofMillis(500);
    private static final Logger LOGGER = LoggerFactory.getLogger(JobExecutorActor.class);

    /**
     * Internal message telling the actor to request a permanent shutdown.
     *
     * This exists because it is unsafe to call `killSelfPermanently` from inside
     * a CompletionStage, since we could be outside an Akka dispatcher thread.
     */
    private static final String REQUEST_PERMANENT_SHUTDOWN = "REQUEST_SHUTDOWN";

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
     * Newtype wrapper around a {@link Reload} to mark its origin as the completion
     * of a job, vs an anti-entropy tick or restart tick.
     *
     * @param <T> The type of the result computed by the referenced {@link Job}.
     */
    private static final class ExecutionCompleted<T> {
        private final Reload<T> _reload;

        ExecutionCompleted(final Reload<T> reload) {
            _reload = reload;
        }

        public Reload<T> getReload() {
            return _reload;
        }
    }

    /**
     * Indicates that a reload has completed, and so we can begin ticking again with both the latest cachedJob
     * and lastRun.
     */
    private static final class RestartTicker {
        private final Optional<Instant> _lastRun;

        RestartTicker(final Optional<Instant> lastRun) {
            _lastRun = lastRun;
        }

        public Optional<Instant> getLastRun() {
            return _lastRun;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("_lastRun", _lastRun)
                    .toString();
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
                return result == null ^ _error == null;
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
