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

import com.arpnetworking.logback.annotations.Loggable;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.base.MoreObjects;
import com.google.inject.Injector;
import models.internal.scheduling.Job;
import models.internal.scheduling.JobExecution;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Wrapper around a {@link JobRef} / {@link Job} that tries to minimize interaction with the underlying repository.
 *
 * @param <T> Type of the result computed by the referenced {@link Job}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
@Loggable
public final class CachedJob<T> implements Job<T> {
    private final JobRef<T> _ref;
    private final PeriodicMetrics _periodicMetrics;
    private Job<T> _cached;
    private Optional<Instant> _lastRun;

    private CachedJob(final JobRef<T> ref, final PeriodicMetrics periodicMetrics) {
        _ref = ref;
        _periodicMetrics = periodicMetrics;
    }

    /**
     * Factory for creating {@link CachedJob}s.
     *
     * @param injector The injector to load the referenced {@link JobRepository} through.
     * @param ref The {@link JobRef} to load.
     * @param periodicMetrics The {@link PeriodicMetrics} instance to log metrics through.
     * @param <T> The type of the result of the referenced {@link Job}.
     * @return A {@link CachedJob}.
     * @throws NoSuchJobException If the job can't be loaded from the repository.
     */
    public static <T> CachedJob<T> createAndLoad(
            final Injector injector,
            final JobRef<T> ref,
            final PeriodicMetrics periodicMetrics) throws NoSuchJobException {
        final CachedJob<T> result = new CachedJob<>(ref, periodicMetrics);
        result.reload(injector);
        return result;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("ref", _ref)
                .add("cached", _cached)
                .add("lastRun", _lastRun)
                .toString();
    }

    public JobRef<T> getRef() {
        return _ref;
    }

    public Job<T> getJob() {
        return _cached;
    }

    public Optional<Instant> getLastRun() {
        return _lastRun;
    }

    /**
     * Unconditionally reloads the cached information from the repository.
     *
     * @param injector The Guice injector to load the repository from.
     * @throws NoSuchJobException If the job can't be loaded from the repository.
     */
    public void reload(final Injector injector) throws NoSuchJobException {
        final Optional<Job<T>> loaded = _ref.get(injector);
        if (!loaded.isPresent()) {
            _periodicMetrics.recordCounter("cached_job_reload_success", 0);
            throw new NoSuchJobException(_ref.toString());
        }
        _periodicMetrics.recordCounter("cached_job_reload_success", 1);
        _cached = loaded.get();
        _lastRun = _ref.getExecutionRepository(injector)
                .getLastCompleted(_ref.getJobId(), _ref.getOrganization())
                .map(JobExecution::getScheduled);
    }

    /**
     * Reloads the cached information from the repository, iff it's out of date.
     *
     * @param injector The Guice injector to load the repository from.
     * @param upToDateETag Checked for equality to the cached job's ETag;
     *   if equal, the cached version is assumed to be up to date and no reload is performed.
     * @throws NoSuchJobException If a reload is necessary but the job can't be loaded from the repository.
     */
    public void reloadIfOutdated(final Injector injector, final String upToDateETag) throws NoSuchJobException {
        final boolean upToDate = _cached.getETag().map(upToDateETag::equals).orElse(false);
        _periodicMetrics.recordCounter("cached_job_conditional_reload_necessary", upToDate ? 1 : 0);
        if (upToDate) {
            return;
        }
        LOGGER.debug()
                .setMessage("job is stale; reloading")
                .addData("jobRef", _ref)
                .addData("oldETag", _cached.getETag())
                .addData("newETag", upToDateETag)
                .log();
        reload(injector);
    }

    @Override
    public UUID getId() {
        return _cached.getId();
    }

    @Override
    public Optional<String> getETag() {
        return _cached.getETag();
    }

    @Override
    public Schedule getSchedule() {
        return _cached.getSchedule();
    }

    @Override
    public Duration getTimeout() {
        return _cached.getTimeout();
    }

    @Override
    public CompletionStage<? extends T> execute(final Injector injector, final Instant scheduled) {
        return _cached.execute(injector, scheduled);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(CachedJob.class);
}
