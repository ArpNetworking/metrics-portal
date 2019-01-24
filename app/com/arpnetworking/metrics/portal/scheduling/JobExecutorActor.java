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

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

/**
 * An actor that executes {@link Job}s.
 *
 * @param <T> The type of result produced by the {@link Job}s.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class JobExecutorActor<T> extends AbstractActorWithTimers {


    private final Injector _injector;
    private final Clock _clock;
    private final PeriodicMetrics _periodicMetrics;
    private final AtomicBoolean _currentlyExecuting = new AtomicBoolean(false);
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
    public void preStart() throws Exception {
        super.preStart();
        timers().startPeriodicTimer(REGULAR_TICK_TIMER_NAME, Tick.INSTANCE, TICK_INTERVAL);
    }

    @Override
    public void postRestart(final Throwable reason) throws Exception {
        super.postRestart(reason);
        timers().startPeriodicTimer(REGULAR_TICK_TIMER_NAME, Tick.INSTANCE, TICK_INTERVAL);
    }

    /**
     * Returns the time until the actor should next wake up.
     *
     * @param wakeUpBy The time we want to be sure we wake up by.
     * @return The time until we should wake up. Always between 0 and (approximately) {@code wakeUpBy}.
     */
    /* package private */ Optional<FiniteDuration> timeUntilExtraTick(final Instant wakeUpBy) {
        final FiniteDuration delta = Duration.fromNanos(ChronoUnit.NANOS.between(_clock.instant(), wakeUpBy));
        if (delta.lt(Duration.Zero())) {
            return Optional.of(Duration.Zero());
        } else if (delta.lt(TICK_INTERVAL)) {
            return Optional.of(delta);
        }
        return Optional.empty();
    }

    private void scheduleExtraTickIfNecessary(final Instant wakeUpBy) {
        timeUntilExtraTick(wakeUpBy).ifPresent(timeRemaining -> {
            timers().startSingleTimer(EXTRA_TICK_TIMER_NAME, Tick.INSTANCE, timeRemaining);
        });
    }

    private void stopForever() {
        // TODO(spencerpearson): Per https://doc.akka.io/docs/akka/2.5.4/java/cluster-sharding.html#remembering-entities ,
        //   might we need to send a Passivate message to our parent?
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
            _cachedJob = Optional.of(new CachedJob<>(_injector, ref));
        }

        final JobRef<T> oldRef = _cachedJob.get().getRef();
        if (!oldRef.equals(ref)) {
            LOGGER.error()
                    .setMessage("JobRef in received message does not match cached JobRef")
                    .addData("cached", oldRef)
                    .addData("new", ref)
                    .log();
            stopForever();
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

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Tick.class, message -> {
                    _periodicMetrics.recordCounter("job-executor-actor-ticks", 1);

                    if (!_cachedJob.isPresent()) {
                        LOGGER.info()
                                .setMessage("uninitialized JobExecutorActor is trying to tick")
                                .log();
                        return;
                    }
                    final CachedJob<T> cachedJob = _cachedJob.get();

                    final Optional<Instant> nextRun = cachedJob.getSchedule().nextRun(cachedJob.getLastRun());
                    if (!nextRun.isPresent()) {
                        LOGGER.info()
                                .setMessage("job has no more scheduled runs")
                                .addData("cachedJob", cachedJob)
                                .log();
                        stopForever();
                        return;
                    }

                    if (_clock.instant().isBefore(nextRun.get())) {
                        scheduleExtraTickIfNecessary(nextRun.get());
                    } else {
                        if (_currentlyExecuting.getAndSet(true)) {
                            cachedJob.execute(getSelf(), nextRun.get())
                                    .whenComplete((result, error) -> _currentlyExecuting.set(false));
                        }
                    }
                })
                .match(Reload.class, message -> {
                    final String eTag = message.getETag();
                    final CachedJob<T> cachedJob;
                    try {
                        cachedJob = initializeOrEnsureRefMatch(unsafeJobRefCast(message.getJobRef()));
                        if (eTag == null) {
                            cachedJob.reload();
                        } else {
                            cachedJob.reloadIfOutdated(eTag);
                        }
                    } catch (final NoSuchElementException error) {
                        LOGGER.warn()
                                .setMessage("job no longer exists in repository; stopping actor")
                                .addData("jobRef", message.getJobRef())
                                .log();
                        stopForever();
                    }
                })
                .build();
    }

    private static final String REGULAR_TICK_TIMER_NAME = "TICK";
    private static final String EXTRA_TICK_TIMER_NAME = "EXTRA_TICK";
    /* package private */ static final String BROADCAST_TOPIC = "JobExecutorActor.broadcast";
    /* package private */ static final FiniteDuration TICK_INTERVAL = Duration.apply(1, TimeUnit.MINUTES);
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

        @Nullable
        public String getETag() {
            return _eTag;
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

}
