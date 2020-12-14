/*
 * Copyright 2020 Dropbox, Inc.
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

import com.arpnetworking.metrics.portal.scheduling.JobExecutionRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.Maps;
import edu.umd.cs.findbugs.annotations.Nullable;
import models.internal.Organization;
import models.internal.scheduling.Job;
import models.internal.scheduling.JobExecution;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@code JobExecutionRepository} backed by a {@link Map}.
 * <p>
 * This repository only holds the most recent execution for a given Job, overwriting one if it exists.
 *
 * @param <T> The result type of the underlying {@link Job}s.
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class MapJobExecutionRepository<T> implements JobExecutionRepository<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapJobRepository.class);
    private final AtomicBoolean _open = new AtomicBoolean();
    private final Map<Organization, Map<UUID, JobExecution<T>>> _lastRuns = Maps.newHashMap();

    @Override
    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("opening JobRepository").log();
        _open.set(true);
    }

    @Override
    public void close() {
        assertIsOpen();
        LOGGER.debug().setMessage("opening JobRepository").log();
        _open.set(false);
    }

    @Override
    public CompletableFuture<Optional<JobExecution<T>>> getLastScheduled(final UUID jobId, final Organization organization) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(_lastRuns.getOrDefault(organization, Collections.emptyMap()).get(jobId))
        );
    }

    @Override
    public CompletableFuture<Optional<JobExecution.Success<T>>> getLastSuccess(final UUID jobId, final Organization organization) {
        final Optional<JobExecution.Success<T>> result = getLastCompletedInner(jobId, organization).flatMap(new JobExecution.Visitor<T, Optional<JobExecution.Success<T>>>() {
            @Override
            public Optional<JobExecution.Success<T>> visit(final JobExecution.Started<T> state) {
                return Optional.empty();
            }

            @Override
            public Optional<JobExecution.Success<T>> visit(final JobExecution.Success<T> state) {
                return Optional.of(state);
            }

            @Override
            public Optional<JobExecution.Success<T>> visit(final JobExecution.Failure<T> state) {
                return Optional.empty();
            }
        });
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Optional<JobExecution<T>>> getLastCompleted(final UUID jobId, final Organization organization) {
        return CompletableFuture.completedFuture(getLastCompletedInner(jobId, organization));
    }

    private Optional<JobExecution<T>> getLastCompletedInner(final UUID jobId, final Organization organization) {
        @Nullable final JobExecution<T> execution = _lastRuns.getOrDefault(organization, Collections.emptyMap()).get(jobId);
        if (execution == null) {
            return Optional.empty();
        }
        return (new JobExecution.Visitor<T, Optional<JobExecution<T>>>() {
            @Override
            public Optional<JobExecution<T>> visit(final JobExecution.Started<T> state) {
                return Optional.empty();
            }

            @Override
            public Optional<JobExecution<T>> visit(final JobExecution.Success<T> state) {
                return Optional.of(state);
            }

            @Override
            public Optional<JobExecution<T>> visit(final JobExecution.Failure<T> state) {
                return Optional.of(state);
            }
        }).apply(execution);
    }

    @Override
    public CompletableFuture<Void> jobStarted(final UUID id, final Organization organization, final Instant scheduled) {
        final JobExecution.Started<T> execution = new JobExecution.Started.Builder<T>()
                .setJobId(id)
                .setScheduled(scheduled)
                .setStartedAt(Instant.now())
                .build();

        assertIsOpen();
        _lastRuns.computeIfAbsent(organization, o -> Maps.newHashMap())
                .compute(id, (id0, t1) -> (t1 == null) ? execution : t1.getScheduled().isAfter(scheduled) ? t1 : execution);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> jobSucceeded(final UUID jobId, final Organization organization, final Instant scheduled, final T result) {
        final JobExecution.Success<T> execution = new JobExecution.Success.Builder<T>()
                .setJobId(jobId)
                .setScheduled(scheduled)
                .setStartedAt(Instant.now())
                .setCompletedAt(Instant.now())
                .setResult(result)
                .build();

        assertIsOpen();
        _lastRuns.computeIfAbsent(organization, o -> Maps.newHashMap())
                .compute(jobId, (id0, t1) -> (t1 == null) ? execution : t1.getScheduled().isAfter(scheduled) ? t1 : execution);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> jobFailed(final UUID jobId, final Organization organization, final Instant scheduled, final Throwable error) {
        final JobExecution.Failure<T> execution = new JobExecution.Failure.Builder<T>()
                .setJobId(jobId)
                .setScheduled(scheduled)
                .setStartedAt(Instant.now())
                .setCompletedAt(Instant.now())
                .setError(error)
                .build();

        assertIsOpen();
        _lastRuns.computeIfAbsent(organization, o -> Maps.newHashMap())
                .compute(jobId, (id0, t1) -> (t1 == null) ? execution : t1.getScheduled().isAfter(scheduled) ? t1 : execution);
        return CompletableFuture.completedFuture(null);
    }

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_open.get() != expectedState) {
            throw new IllegalStateException("MapJobRepository is not " + (expectedState ? "open" : "closed"));
        }
    }
}
