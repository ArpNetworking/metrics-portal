/*
 * Copyright 2021 Dropbox, Inc.
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
package com.arpnetworking.metrics.portal.alerts.impl;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.pattern.Patterns;
import com.arpnetworking.commons.akka.AkkaJsonSerializable;
import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.scheduling.JobExecution;
import net.sf.oval.constraint.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * An actor that acts as a simple key-value store for alert executions.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class AlertExecutionCacheActor extends AbstractActor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlertExecutionCacheActor.class);

    private final PeriodicMetrics _metrics;
    private final Cache<UUID, JobExecution.Success<AlertEvaluationResult>> _cache;

    private AlertExecutionCacheActor(
            final PeriodicMetrics metrics,
            final int maxSize,
            final Duration expireAfterAccess
    ) {
        _metrics = metrics;
        _cache = CacheBuilder.newBuilder()
            .maximumSize(maxSize)
            .expireAfterAccess(expireAfterAccess.getSeconds(), TimeUnit.SECONDS)
            .build();
    }

    /**
     * Props to construct a new instance of {@code AlertExecutionCacheActor}.
     *
     * @param metrics A metrics instance to record against.
     * @param maxSize the maximum cache size
     * @param expireAfterAccess expiry time for values starting from last access
     *
     * @return props to instantiate the actor
     */
    public static Props props(
            final PeriodicMetrics metrics,
            final int maxSize,
            final Duration expireAfterAccess
    ) {
        return Props.create(AlertExecutionCacheActor.class, () -> new AlertExecutionCacheActor(metrics, maxSize, expireAfterAccess));
    }

    /**
     * Retrieve the execution associated with this job id.
     *
     * @param ref The CacheActor ref.
     * @param jobId The job to retrieve.
     * @param timeout The operation timeout.
     * @return A CompletionStage which contains the execution, if any.
     */
    @SuppressWarnings("unchecked")
    public static CompletionStage<Optional<JobExecution.Success<AlertEvaluationResult>>> get(
            final ActorRef ref,
            final UUID jobId,
            final Duration timeout
    ) {
        return Patterns.ask(
            ref,
            new CacheGet.Builder().setKey(jobId).build(),
            timeout
        ).thenApply(resp -> ((Optional<SuccessfulAlertExecution>) resp).map(SuccessfulAlertExecution::toJobExecution));
    }

    /**
     * Retrieve the executions associated with these jobIds.
     *
     * @param ref The CacheActor ref.
     * @param jobIds The jobs to retrieve.
     * @param timeout The operation timeout.
     * @return A CompletionStage which contains the executions, if any.
     */
    @SuppressWarnings("unchecked")
    public static CompletionStage<ImmutableMap<UUID, JobExecution.Success<AlertEvaluationResult>>> multiget(
            final ActorRef ref,
            final Collection<UUID> jobIds,
            final Duration timeout
    ) {
        return Patterns.ask(
                ref,
                new CacheMultiGet.Builder().setKeys(ImmutableList.copyOf(jobIds)).build(),
                timeout
        ).thenApply(resp ->
            ((ImmutableMap<UUID, SuccessfulAlertExecution>) resp).entrySet()
                    .stream()
                    .collect(ImmutableMap.toImmutableMap(
                            Map.Entry::getKey,
                            e -> e.getValue().toJobExecution()
                    ))
        );
    }

    /**
     * Put an execution into the cache.
     *
     * @param ref The CacheActor ref.
     * @param key The key to update.
     * @param value The execution.
     * @param timeout The operation timeout.
     * @return A completion stage to await for the write to complete.
     */
    public static CompletionStage<Void> put(
            final ActorRef ref,
            final UUID key,
            final JobExecution.Success<AlertEvaluationResult> value,
            final Duration timeout
    ) {
        return Patterns.ask(
            ref,
            new CachePut.Builder().setExecution(SuccessfulAlertExecution.Builder.copyJobExecution(value).build()).build(),
            timeout
        ).thenApply(resp -> null);
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        LOGGER.info()
                .setMessage("cache actor starting")
                .log();
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();
        LOGGER.info()
                .setMessage("cache actor was stopped")
                .log();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(CacheGet.class, msg -> {
                final Optional<SuccessfulAlertExecution> value =
                        Optional.ofNullable(_cache.getIfPresent(msg.getKey()))
                            .map(e -> SuccessfulAlertExecution.Builder.copyJobExecution(e).build());
                _metrics.recordCounter("cache/alert-execution-cache/get", value.isPresent() ? 1 : 0);
                sender().tell(value, getSelf());
            })
            .match(CacheMultiGet.class, msg -> {
                final ImmutableMap.Builder<UUID, SuccessfulAlertExecution> results = new ImmutableMap.Builder<>();
                int hits = 0;
                for (final UUID key : msg.getKeys()) {
                    final Optional<JobExecution.Success<AlertEvaluationResult>> value = Optional.ofNullable(_cache.getIfPresent(key));
                    if (value.isPresent()) {
                        hits += 1;
                        results.put(key, SuccessfulAlertExecution.Builder.copyJobExecution(value.get()).build());
                    }
                }
                _metrics.recordCounter("cache/alert-execution-cache/multiget", hits);
                sender().tell(results.build(), getSelf());
            })
            .match(CachePut.class, msg -> {
                _metrics.recordCounter("cache/alert-execution-cache/put", 1);
                final ActorRef self = getSelf();
                final JobExecution.Success<AlertEvaluationResult> value = msg.getExecution().toJobExecution();
                _cache.put(value.getJobId(), value);
                sender().tell(new Status.Success(null), self);
            })
            .build();
    }

    private static final class CacheGet implements AkkaJsonSerializable {
        private final UUID _key;

        /**
         * Constructor.
         */
        private CacheGet(final Builder builder) {
            _key = builder._key;
        }

        public UUID getKey() {
            return _key;
        }

        public static final class Builder extends OvalBuilder<CacheGet> {
            @NotNull
            private UUID _key;

            Builder() {
                super(CacheGet::new);
            }

            public Builder setKey(final UUID key) {
                _key = key;
                return this;
            }
        }
    }

    private static final class CacheMultiGet implements AkkaJsonSerializable {
        private final Collection<UUID> _keys;

        private CacheMultiGet(final Builder builder) {
            _keys = builder._keys;
        }

        public Collection<UUID> getKeys() {
            return _keys;
        }

        private static final class Builder extends OvalBuilder<CacheMultiGet> {
            @NotNull
            private ImmutableList<UUID> _keys;

            Builder() {
                super(CacheMultiGet::new);
            }

            public Builder setKeys(final ImmutableList<UUID> keys) {
                _keys = keys;
                return this;
            }
        }
    }

    private static final class CachePut implements AkkaJsonSerializable {
        private final SuccessfulAlertExecution _execution;

        private CachePut(final Builder builder) {
            _execution = builder._execution;
        }

        public SuccessfulAlertExecution getExecution() {
            return _execution;
        }

        public static final class Builder extends OvalBuilder<CachePut> {
            private SuccessfulAlertExecution _execution;

            Builder() {
                super(CachePut::new);
            }

            public Builder setExecution(final SuccessfulAlertExecution execution) {
                _execution = execution;
                return this;
            }
        }
    }

    /**
     * Specialization of {@code JobExecution.Success<AlertEvaluationResult>} - they
     * should be kept in sync.
     * <br>
     * Copying the values into a non-generic class removes any of the problems of
     * generic serialization, even if it is a bit unergonomic.
     */
    private static final class SuccessfulAlertExecution {
        private final UUID _jobId;
        private final Instant _scheduled;
        private final Instant _startedAt;
        private final Instant _completedAt;
        private final AlertEvaluationResult _result;

        public UUID getJobId() {
            return _jobId;
        }

        public Instant getScheduled() {
            return _scheduled;
        }

        public Instant getStartedAt() {
            return _startedAt;
        }

        public Instant getCompletedAt() {
            return _completedAt;
        }

        public AlertEvaluationResult getResult() {
            return _result;
        }

        public JobExecution.Success<AlertEvaluationResult> toJobExecution() {
            return new JobExecution.Success.Builder<AlertEvaluationResult>()
                .setJobId(_jobId)
                .setScheduled(_scheduled)
                .setStartedAt(_startedAt)
                .setCompletedAt(_completedAt)
                .setResult(_result)
                .build();
        }

        private SuccessfulAlertExecution(final Builder builder) {
            _jobId = builder._jobId;
            _scheduled = builder._scheduled;
            _startedAt = builder._startedAt;
            _completedAt = builder._completedAt;
            _result = builder._result;
        }

        private static final class Builder extends OvalBuilder<SuccessfulAlertExecution> {
            private UUID _jobId;
            private Instant _scheduled;
            private Instant _startedAt;
            private Instant _completedAt;
            private AlertEvaluationResult _result;

            Builder() {
                super(SuccessfulAlertExecution::new);
            }

            public static Builder copyJobExecution(final JobExecution.Success<AlertEvaluationResult> value) {
                return new Builder()
                    .setJobId(value.getJobId())
                    .setResult(value.getResult())
                    .setScheduled(value.getScheduled())
                    .setStartedAt(value.getStartedAt())
                    .setCompletedAt(value.getCompletedAt());
            }

            public Builder setJobId(final UUID jobId) {
                _jobId = jobId;
                return this;
            }

            public Builder setResult(final AlertEvaluationResult result) {
                _result = result;
                return this;
            }

            public Builder setScheduled(final Instant scheduled) {
                _scheduled = scheduled;
                return this;
            }

            public Builder setStartedAt(final Instant startedAt) {
                _startedAt = startedAt;
                return this;
            }

            public Builder setCompletedAt(final Instant completedAt) {
                _completedAt = completedAt;
                return this;
            }
        }
    }
}
