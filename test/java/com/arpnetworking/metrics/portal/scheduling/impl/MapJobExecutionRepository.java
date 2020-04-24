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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@code JobExecutionRepository} backed by a {@link Map}.
 *
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
    public Optional<JobExecution.Success<T>> getLastSuccess(final UUID jobId, final Organization organization) throws NoSuchElementException {
        return getLastCompleted(jobId, organization).flatMap(execution -> {
            return (new JobExecution.Visitor<T, Optional<JobExecution.Success<T>>>() {
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
            }).visit(execution);
        });
    }

    @Override
    public Optional<JobExecution<T>> getLastCompleted(final UUID jobId, final Organization organization) throws NoSuchElementException {
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
        }).visit(execution);
    }

    @Override
    public void jobStarted(final UUID id, final Organization organization, final Instant scheduled) {
        final JobExecution.Started<T> execution = new JobExecution.Started.Builder<T>()
                .setJobId(id)
                .setScheduled(scheduled)
                .setStartedAt(Instant.now())
                .build();

        assertIsOpen();
        _lastRuns.computeIfAbsent(organization, o -> Maps.newHashMap())
                .compute(id, (id0, t1) -> (t1 == null) ? execution : t1.getScheduled().isAfter(scheduled) ? t1 : execution);
    }

    @Override
    public void jobSucceeded(final UUID jobId, final Organization organization, final Instant scheduled, final T result) {
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
    }

    @Override
    public void jobFailed(final UUID jobId, final Organization organization, final Instant scheduled, final Throwable error) {
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
