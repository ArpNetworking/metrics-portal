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

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.metrics.portal.alerts.AlertExecutionRepository;
import com.arpnetworking.notcommons.java.time.TimeAdapters;
import com.fasterxml.jackson.annotation.JacksonInject;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import models.internal.Organization;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.scheduling.JobExecution;
import net.sf.oval.constraint.NotNull;
import org.apache.pekko.actor.ActorRef;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;
import javax.inject.Named;

/**
 * An alert repository wrapper that caches the most recent successful results.
 * <br>
 * {@link AlertExecutionRepository#getLastSuccessBatch} is probably the most frequently
 * accessed piece of execution data for alerts, so it's useful to cache those results
 * separately.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class CachingAlertExecutionRepository implements AlertExecutionRepository {
    private final AlertExecutionRepository _inner;
    private final ActorRef _successCache;
    private final Duration _cacheOperationTimeout;

    private CachingAlertExecutionRepository(final Builder builder) {
        _inner = builder._inner;
        _successCache = builder._actorRef;
        _cacheOperationTimeout = builder._operationTimeout;
    }

    @Override
    public void open() {
        _inner.open();
    }

    @Override
    public void close() {
        _inner.close();
    }

    @Override
    public CompletionStage<Optional<JobExecution<AlertEvaluationResult>>> getLastScheduled(
        final UUID jobId, final Organization organization
    ) {
        return _inner.getLastScheduled(jobId, organization);
    }

    @Override
    public CompletionStage<Optional<JobExecution.Success<AlertEvaluationResult>>> getLastSuccess(
        final UUID jobId, final Organization organization
    ) throws NoSuchElementException {
        return
            AlertExecutionCacheActor.get(_successCache, organization, jobId, _cacheOperationTimeout)
                .thenCompose(res -> {
                    if (res.isPresent()) {
                        return CompletableFuture.completedFuture(res);
                    }
                    return _inner.getLastSuccess(jobId, organization)
                        .thenCompose(res2 -> {
                            if (!res2.isPresent()) {
                                return CompletableFuture.completedFuture(res2);
                            }
                            return AlertExecutionCacheActor.put(
                                    _successCache,
                                    organization,
                                    res2.get(),
                                    _cacheOperationTimeout
                            ).thenApply(ignore -> res2);
                        });
                });
    }

    @Override
    public CompletionStage<ImmutableMap<UUID, JobExecution.Success<AlertEvaluationResult>>> getLastSuccessBatch(
        final List<UUID> jobIds,
        final Organization organization,
        final LocalDate maxLookback
    ) throws NoSuchElementException {
        // Attempt to get all ids from cache
        final CompletionStage<ImmutableMap<UUID, JobExecution.Success<AlertEvaluationResult>>> cached =
                AlertExecutionCacheActor.multiget(
                    _successCache,
                    organization,
                    jobIds,
                    _cacheOperationTimeout
                );

        return cached.thenCompose(hits -> {
            // Check for any cache misses and fetch those from the inner
            // repository.
            final List<UUID> misses =
                jobIds.stream()
                    .filter(Predicates.in(hits.keySet()).negate())
                    .collect(ImmutableList.toImmutableList());
            if (misses.isEmpty()) {
                return CompletableFuture.completedFuture(hits);
            }
            return _inner
                .getLastSuccessBatch(misses, organization, maxLookback)
                .whenComplete((rest, error) -> {
                    if (error != null) {
                        return;
                    }
                    // Write back to cache.
                    //
                    // Note that we do not block on the cache update.
                    AlertExecutionCacheActor.multiput(_successCache, organization, rest.values(), _cacheOperationTimeout);
                })
                .thenApply(rest -> {
                    // Merge the cache hits / misses into a single map.
                    return Stream.concat(
                        hits.entrySet().stream(),
                        rest.entrySet().stream()
                    ).collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
                });
        });
    }

    @Override
    public CompletionStage<Optional<JobExecution<AlertEvaluationResult>>> getLastCompleted(
        final UUID jobId, final Organization organization
    ) throws NoSuchElementException {
        return _inner.getLastCompleted(jobId, organization);
    }

    @Override
    public CompletionStage<Void> jobStarted(
        final UUID jobId,
        final Organization organization,
        final Instant scheduled
    ) {
        return _inner.jobStarted(jobId, organization, scheduled);
    }

    @Override
    public CompletionStage<JobExecution.Success<AlertEvaluationResult>> jobSucceeded(
        final UUID jobId, final Organization organization, final Instant scheduled, final AlertEvaluationResult result
    ) {
        return _inner.jobSucceeded(jobId, organization, scheduled, result)
            .thenCompose(e -> AlertExecutionCacheActor.put(_successCache, organization, e, _cacheOperationTimeout).thenApply(ignore -> e));
    }

    @Override
    public CompletionStage<Void> jobFailed(
        final UUID jobId, final Organization organization, final Instant scheduled, final Throwable error
    ) {
        return _inner.jobFailed(jobId, organization, scheduled, error);
    }

    /**
     * Builder for instances of {@link CachingAlertExecutionRepository}.
     */
    public static final class Builder extends OvalBuilder<CachingAlertExecutionRepository> {
        @NotNull
        private AlertExecutionRepository _inner;
        @NotNull
        private Duration _operationTimeout = Duration.ofSeconds(5);
        @NotNull
        private ActorRef _actorRef;

        /**
         * Construct a Builder with default values.
         */
        public Builder() {
            super(CachingAlertExecutionRepository::new);
        }

        /**
         * Sets the inner repository. Required. Cannot be null.
         * @param inner The inner repository
         * @return This instance of {@code Builder} for chaining.
         */
        public Builder setInner(final AlertExecutionRepository inner) {
            _inner = inner;
            return this;
        }

        /**
         * Sets the cache operation timeout. Optional. Default is 5 seconds.
         *
         * @param timeout The cache timeout in FiniteDuration form
         * @return This instance of {@code Builder} for chaining.
         */
        public Builder setOperationTimeout(final String timeout) {
            final scala.concurrent.duration.Duration scalaDuration = scala.concurrent.duration.Duration.apply(timeout);
            _operationTimeout = Duration.of(scalaDuration.length(), TimeAdapters.toChronoUnit(scalaDuration.unit()));
            return this;
        }

        /**
         * Sets the actor ref. Required. Cannot be null.
         * @param actorRef The actor ref
         * @return This instance of {@code Builder} for chaining.
         */
        @JacksonInject
        public Builder setActorRef(@Named("AlertExecutionCache") final ActorRef actorRef) {
            _actorRef = actorRef;
            return this;
        }
    }
}
