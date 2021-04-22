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

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.pattern.Patterns;
import akka.persistence.AbstractPersistentActorWithTimers;
import akka.persistence.SaveSnapshotFailure;
import akka.persistence.SaveSnapshotSuccess;
import akka.persistence.SnapshotMetadata;
import akka.persistence.SnapshotOffer;
import com.arpnetworking.commons.akka.AkkaJsonSerializable;
import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.base.Objects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import models.internal.Organization;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.scheduling.JobExecution;
import net.sf.oval.constraint.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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
public final class AlertExecutionCacheActor extends AbstractPersistentActorWithTimers {
    private static final String MSG_TAKE_SNAPSHOT = "MSG_TAKE_SNAPSHOT";
    private static final String SNAPSHOT_TIMER_KEY = "SNAPSHOT_TIMER_KEY";
    private static final Duration SNAPSHOT_INTERVAL = Duration.ofMinutes(5);
    private static final Logger LOGGER = LoggerFactory.getLogger(AlertExecutionCacheActor.class);

    private final PeriodicMetrics _metrics;
    private final Cache<CacheKey, JobExecution.Success<AlertEvaluationResult>> _cache;

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
     * @param organization the organization containing this job id
     * @param jobId The job to retrieve.
     * @param timeout The operation timeout.
     * @return A CompletionStage which contains the execution, if any.
     */
    public static CompletionStage<Optional<JobExecution.Success<AlertEvaluationResult>>> get(
            final ActorRef ref,
            final Organization organization,
            final UUID jobId,
            final Duration timeout
    ) {
        return Patterns.ask(
            ref,
            new CacheGet.Builder()
                    .setJobId(jobId)
                    .setOrganizationId(organization.getId())
                    .build(),
            timeout
        ).thenApply(resp -> ((CacheGetResponse) resp).getExecution().map(SuccessfulAlertExecution::toJobExecution));
    }

    /**
     * Retrieve the executions associated with these jobIds.
     *
     * @param ref The CacheActor ref.
     * @param organization the organization containing these job ids.
     * @param jobIds The jobs to retrieve.
     * @param timeout The operation timeout.
     * @return A CompletionStage which contains the executions, if any.
     */
    public static CompletionStage<ImmutableMap<UUID, JobExecution.Success<AlertEvaluationResult>>> multiget(
            final ActorRef ref,
            final Organization organization,
            final Collection<UUID> jobIds,
            final Duration timeout
    ) {
        return Patterns.ask(
                ref,
                new CacheMultiGet.Builder()
                        .setJobIds(ImmutableList.copyOf(jobIds))
                        .setOrganizationId(organization.getId())
                        .build(),
                timeout
        ).thenApply(resp ->
            ((CacheMultiGetResponse) resp)
                    .getExecutions()
                    .entrySet()
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
     * @param organization The organization.
     * @param value The execution.
     * @param timeout The operation timeout.
     * @return A completion stage to await for the write to complete.
     */
    public static CompletionStage<Void> put(
            final ActorRef ref,
            final Organization organization,
            final JobExecution.Success<AlertEvaluationResult> value,
            final Duration timeout
    ) {
        return Patterns.ask(
            ref,
            new CachePut.Builder()
                    .setExecution(SuccessfulAlertExecution.Builder.copyJobExecution(value).build())
                    .setOrganizationId(organization.getId())
                    .build(),
            timeout
        ).thenApply(resp -> null);
    }

    /**
     * Put several executions into the cache.
     *
     * @param ref The CacheActor ref.
     * @param organization The organization.
     * @param executions The executions.
     * @param timeout The operation timeout.
     * @return A completion stage to await for the write to complete.
     */
    public static CompletionStage<Void> multiput(
            final ActorRef ref,
            final Organization organization,
            final Collection<JobExecution.Success<AlertEvaluationResult>> executions,
            final Duration timeout
    ) {
        final ImmutableList<SuccessfulAlertExecution> msgExecutions =
                executions.stream()
                        .map(e -> SuccessfulAlertExecution.Builder.copyJobExecution(e).build())
                        .collect(ImmutableList.toImmutableList());
        return Patterns.ask(
                ref,
                new CacheMultiPut.Builder()
                        .setExecutions(msgExecutions)
                        .setOrganizationId(organization.getId())
                        .build(),
                timeout
        ).thenApply(resp -> null);
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        LOGGER.info()
                .setMessage("cache actor starting")
                .log();
        timers().startPeriodicTimer(
                SNAPSHOT_TIMER_KEY,
                MSG_TAKE_SNAPSHOT,
                SNAPSHOT_INTERVAL
        );
    }

    @Override
    public void postStop() {
        super.postStop();
        LOGGER.info()
                .setMessage("cache actor was stopped")
                .log();
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
                .match(SnapshotOffer.class, offer -> {
                    final SnapshotMetadata metadata = offer.metadata();
                    LOGGER.info()
                        .setMessage("reloading cache from snapshot")
                        .addData("sequenceNr", metadata.sequenceNr())
                        .addData("timestamp", Instant.ofEpochMilli(metadata.timestamp()))
                        .addData("persistenceId", metadata.persistenceId())
                        .log();
                    final CacheSnapshot snapshot = (CacheSnapshot) offer.snapshot();
                    for (final SnapshotEntry entry : snapshot.getEntries()) {
                        _cache.put(entry.getKey(), entry.getValue());
                    }
                })
                .match(CacheMultiPut.class, this::handleMultiPut)
                .match(CachePut.class, this::handlePut)
                .build();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(CacheGet.class, msg -> {
                final CacheKey cacheKey = new CacheKey.Builder()
                        .setOrganizationId(msg.getOrganizationId())
                        .setJobId(msg.getJobId())
                        .build();
                final Optional<SuccessfulAlertExecution> value =
                        Optional.ofNullable(_cache.getIfPresent(cacheKey))
                            .map(e -> SuccessfulAlertExecution.Builder.copyJobExecution(e).build());
                _metrics.recordCounter("cache/alert-execution-cache/get", value.isPresent() ? 1 : 0);
                final CacheGetResponse resp = new CacheGetResponse.Builder().setExecution(value).build();
                sender().tell(resp, getSelf());
            })
            .match(CacheMultiGet.class, msg -> {
                final ImmutableMap.Builder<UUID, SuccessfulAlertExecution> results = new ImmutableMap.Builder<>();
                for (final UUID jobId : msg.getJobIds()) {
                    final CacheKey cacheKey = new CacheKey.Builder()
                            .setOrganizationId(msg.getOrganizationId())
                            .setJobId(jobId)
                            .build();
                    final Optional<JobExecution.Success<AlertEvaluationResult>> value = Optional.ofNullable(_cache.getIfPresent(cacheKey));
                    value.ifPresent(alertEvaluationResultSuccess ->
                            results.put(jobId, SuccessfulAlertExecution.Builder.copyJobExecution(alertEvaluationResultSuccess).build())
                    );
                    _metrics.recordCounter("cache/alert-execution-cache/get", value.isPresent() ? 1 : 0);
                }
                final CacheMultiGetResponse resp = new CacheMultiGetResponse.Builder().setExecutions(results.build()).build();
                sender().tell(resp, getSelf());
            })
            .match(CacheMultiPut.class, msg -> {
                persist(msg, m -> {
                    handleMultiPut(msg);
                    _metrics.recordCounter("cache/alert-execution-cache/put", msg.getExecutions().size());
                    sender().tell(new Status.Success(null), getSelf());
                });
            })
            .match(CachePut.class, msg -> {
                persist(msg, m -> {
                    handlePut(msg);
                    _metrics.recordCounter("cache/alert-execution-cache/put", 1);
                    sender().tell(new Status.Success(null), getSelf());
                });
            })
            .matchEquals(MSG_TAKE_SNAPSHOT, m -> {
                LOGGER.info("cache snapshot requested.");
                final ImmutableList<SnapshotEntry> snapshotEntries = _cache.asMap()
                        .entrySet()
                        .stream()
                        .map(e -> new SnapshotEntry.Builder().setKey(e.getKey()).setValue(e.getValue()).build())
                        .collect(ImmutableList.toImmutableList());
                final CacheSnapshot snapshot = new CacheSnapshot.Builder()
                        .setEntries(snapshotEntries)
                        .build();
                saveSnapshot(snapshot);
            })
            .match(SaveSnapshotSuccess.class, m -> LOGGER.info("successfully saved cache snapshot."))
            .match(SaveSnapshotFailure.class, m -> LOGGER.error()
                    .addData("cause", m.cause())
                    .setMessage("failed to save cache snapshot.")
                    .log()
            ).build();
    }

    @Override
    public String persistenceId() {
        return "alert-execution-cache";
    }

    private void handleMultiPut(final CacheMultiPut msg) {
        for (final SuccessfulAlertExecution execution : msg.getExecutions()) {
            final CacheKey cacheKey = new CacheKey.Builder()
                    .setOrganizationId(msg.getOrganizationId())
                    .setJobId(execution.getJobId())
                    .build();
            _cache.put(cacheKey, execution.toJobExecution());
        }
    }

    private void handlePut(final CachePut msg) {
        final CacheKey cacheKey = new CacheKey.Builder()
            .setOrganizationId(msg.getOrganizationId())
            .setJobId(msg.getExecution().getJobId())
            .build();
        _cache.put(cacheKey, msg.getExecution().toJobExecution());
    }

    // Snapshot of the cache for persistence.
    //
    // This class exists as a wrapper for serialization purposes only.
    private static final class CacheSnapshot implements AkkaJsonSerializable {
        private final ImmutableList<SnapshotEntry> _entries;

        private CacheSnapshot(final Builder builder) {
            _entries = builder._entries;
        }

        public ImmutableList<SnapshotEntry> getEntries() {
            return _entries;
        }

        private static final class Builder extends OvalBuilder<CacheSnapshot> {
            @NotNull
            private ImmutableList<SnapshotEntry> _entries;

            Builder() {
                super(CacheSnapshot::new);
            }

            public Builder setEntries(final List<SnapshotEntry> entries) {
                _entries = ImmutableList.copyOf(entries);
                return this;
            }
        }
    }

    private static final class SnapshotEntry implements AkkaJsonSerializable {
        private final CacheKey _key;
        private final JobExecution.Success<AlertEvaluationResult> _value;

        private SnapshotEntry(final Builder builder) {
            _key = builder._key;
            _value = builder._value;
        }

        public CacheKey getKey() {
            return _key;
        }

        public JobExecution.Success<AlertEvaluationResult> getValue() {
            return _value;
        }

        public static final class Builder extends OvalBuilder<SnapshotEntry> {
            @NotNull
            private CacheKey _key;
            @NotNull
            private JobExecution.Success<AlertEvaluationResult> _value;

            Builder() {
                super(SnapshotEntry::new);
            }

            public Builder setKey(final CacheKey key) {
                _key = key;
                return this;
            }

            public Builder setValue(final JobExecution.Success<AlertEvaluationResult> value) {
                _value = value;
                return this;
            }
        }
    }

    private static final class CacheKey implements AkkaJsonSerializable {
        private final UUID _organizationId;
        private final UUID _jobId;

        private CacheKey(final Builder builder) {
            _organizationId = builder._organizationId;
            _jobId = builder._jobId;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final CacheKey cacheKey = (CacheKey) o;
            return Objects.equal(_organizationId, cacheKey._organizationId)
                    && Objects.equal(_jobId, cacheKey._jobId);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(_organizationId, _jobId);
        }

        public UUID getOrganizationId() {
            return _organizationId;
        }

        public UUID getJobId() {
            return _jobId;
        }

        public static final class Builder extends OvalBuilder<CacheKey> {
            @NotNull
            private UUID _jobId;
            @NotNull
            private UUID _organizationId;

            Builder() {
                super(CacheKey::new);
            }

            public Builder setJobId(final UUID jobId) {
                _jobId = jobId;
                return this;
            }

            public Builder setOrganizationId(final UUID organizationId) {
                _organizationId = organizationId;
                return this;
            }
        }
    }

    private static final class CacheGet implements AkkaJsonSerializable {
        private final UUID _jobId;
        private final UUID _organizationId;

        /**
         * Constructor.
         */
        private CacheGet(final Builder builder) {
            _jobId = builder._jobId;
            _organizationId = builder._organizationId;
        }

        public UUID getJobId() {
            return _jobId;
        }

        public UUID getOrganizationId() {
            return _organizationId;
        }

        public static final class Builder extends OvalBuilder<CacheGet> {
            @NotNull
            private UUID _jobId;
            @NotNull
            private UUID _organizationId;

            Builder() {
                super(CacheGet::new);
            }

            public Builder setJobId(final UUID jobId) {
                _jobId = jobId;
                return this;
            }

            public Builder setOrganizationId(final UUID organizationId) {
                _organizationId = organizationId;
                return this;
            }
        }
    }

    private static final class CacheGetResponse implements AkkaJsonSerializable {
        private final Optional<SuccessfulAlertExecution> _execution;

        public Optional<SuccessfulAlertExecution> getExecution() {
            return _execution;
        }

        private CacheGetResponse(final Builder builder) {
            _execution = builder._execution;
        }

        private static final class Builder extends OvalBuilder<CacheGetResponse> {
            @NotNull
            private Optional<SuccessfulAlertExecution> _execution;

            Builder() {
                super(CacheGetResponse::new);
            }

            public Builder setExecution(final Optional<SuccessfulAlertExecution> execution) {
                _execution = execution;
                return this;
            }
        }

    }

    private static final class CacheMultiGet implements AkkaJsonSerializable {
        private final Collection<UUID> _jobIds;
        private final UUID _organizationId;

        private CacheMultiGet(final Builder builder) {
            _jobIds = builder._jobIds;
            _organizationId = builder._organizationId;
        }

        public Collection<UUID> getJobIds() {
            return _jobIds;
        }

        public UUID getOrganizationId() {
            return _organizationId;
        }

        private static final class Builder extends OvalBuilder<CacheMultiGet> {
            @NotNull
            private ImmutableList<UUID> _jobIds;

            @NotNull
            private UUID _organizationId;

            Builder() {
                super(CacheMultiGet::new);
            }

            public Builder setJobIds(final ImmutableList<UUID> jobIds) {
                _jobIds = jobIds;
                return this;
            }

            public Builder setOrganizationId(final UUID organizationId) {
                _organizationId = organizationId;
                return this;
            }
        }
    }

    private static final class CacheMultiGetResponse implements AkkaJsonSerializable {
        private final ImmutableMap<UUID, SuccessfulAlertExecution> _executions;

        public ImmutableMap<UUID, SuccessfulAlertExecution> getExecutions() {
            return _executions;
        }

        private CacheMultiGetResponse(final Builder builder) {
            _executions = builder._executions;
        }

        private static final class Builder extends OvalBuilder<CacheMultiGetResponse> {
            @NotNull
            private ImmutableMap<UUID, SuccessfulAlertExecution> _executions;

            Builder() {
                super(CacheMultiGetResponse::new);
            }

            public Builder setExecutions(final ImmutableMap<UUID, SuccessfulAlertExecution> executions) {
                _executions = executions;
                return this;
            }
        }

    }

    private static final class CacheMultiPut implements AkkaJsonSerializable {
        private final UUID _organizationId;
        private final Collection<SuccessfulAlertExecution> _executions;

        private CacheMultiPut(final Builder builder) {
            _executions = builder._executions;
            _organizationId = builder._organizationId;
        }

        public UUID getOrganizationId() {
            return _organizationId;
        }

        public Collection<SuccessfulAlertExecution> getExecutions() {
            return _executions;
        }

        private static final class Builder extends OvalBuilder<CacheMultiPut> {
            @NotNull
            private UUID _organizationId;
            @NotNull
            private ImmutableList<SuccessfulAlertExecution> _executions;

            Builder() {
                super(CacheMultiPut::new);
            }

            public Builder setOrganizationId(final UUID organizationId) {
                _organizationId = organizationId;
                return this;
            }

            public Builder setExecutions(final ImmutableList<SuccessfulAlertExecution> executions) {
                _executions = executions;
                return this;
            }
        }
    }

    private static final class CachePut implements AkkaJsonSerializable {
        private final SuccessfulAlertExecution _execution;
        private final UUID _organizationId;

        private CachePut(final Builder builder) {
            _execution = builder._execution;
            _organizationId = builder._organizationId;
        }

        public SuccessfulAlertExecution getExecution() {
            return _execution;
        }

        public UUID getOrganizationId() {
            return _organizationId;
        }

        public static final class Builder extends OvalBuilder<CachePut> {
            @NotNull
            private SuccessfulAlertExecution _execution;
            @NotNull
            private UUID _organizationId;

            Builder() {
                super(CachePut::new);
            }

            public Builder setExecution(final SuccessfulAlertExecution execution) {
                _execution = execution;
                return this;
            }

            public Builder setOrganizationId(final UUID organizationId) {
                _organizationId = organizationId;
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
            @NotNull
            private UUID _jobId;
            @NotNull
            private Instant _scheduled;
            @NotNull
            private Instant _startedAt;
            @NotNull
            private Instant _completedAt;
            @NotNull
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
