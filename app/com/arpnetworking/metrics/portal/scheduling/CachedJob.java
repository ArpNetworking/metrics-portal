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

import akka.actor.ActorRef;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.base.MoreObjects;
import com.google.inject.Injector;
import models.internal.scheduling.Job;

import java.time.Instant;
import java.util.NoSuchElementException;
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
public final class CachedJob<T> implements Job<T> {
    private final Injector _injector;
    private final JobRef<T> _ref;
    private Job<T> _cached;
    private Optional<Instant> _lastRun;

    CachedJob(final Injector injector, final JobRef<T> ref) {
        _injector = injector;
        _ref = ref;
        reload();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("ref", _ref)
                .add("cached", _cached)
                .add("lastRun", _lastRun)
                .add("injector", _injector)
                .toString();
    }

    public JobRef<T> getRef() {
        return _ref;
    }

    public Optional<Instant> getLastRun() {
        return _lastRun;
    }

    public Optional<Instant> getNextRun() {
        return _cached.getSchedule().nextRun(_lastRun);
    }

    /**
     * Unconditionally reloads the cached information from the repository.
     */
    public void reload() {
        final Optional<Job<T>> loaded = _ref.get(_injector);
        if (!loaded.isPresent()) {
            throw new NoSuchElementException(_ref.toString());
        }
        _cached = loaded.get();
        _lastRun = _ref.getRepository(_injector).getLastRun(_ref.getJobId(), _ref.getOrganization());
    }

    /**
     * Reloads the cached information from the repository, iff it's out of date.
     *
     * @param upToDateETag Checked for equality to the cached job's ETag;
     *   if equal, the cached version is assumed to be up to date and no reload is performed.
     */
    public void reloadIfOutdated(final String upToDateETag) {
        if (_cached.getETag().equals(upToDateETag)) {
            return;
        }
        LOGGER.debug()
                .setMessage("job is stale; reloading")
                .addData("jobRef", _ref)
                .addData("oldETag", _cached.getETag())
                .addData("newETag", upToDateETag)
                .log();
        reload();
    }

    @Override
    public UUID getId() {
        return _cached.getId();
    }

    @Override
    public String getETag() {
        return _cached.getETag();
    }

    @Override
    public Schedule getSchedule() {
        return _cached.getSchedule();
    }

    @Override
    public CompletionStage<T> execute(final ActorRef scheduler, final Instant scheduled) {
        final JobRepository<T> repo = _ref.getRepository(_injector);
        repo.jobStarted(_cached.getId(), _ref.getOrganization(), scheduled);
        return _cached.execute(scheduler, scheduled)
                .handle((result, error) -> {
                    _lastRun = Optional.of(scheduled);
                    if (error == null) {
                        repo.jobSucceeded(_cached.getId(), _ref.getOrganization(), scheduled, result);
                    } else {
                        repo.jobFailed(_cached.getId(), _ref.getOrganization(), scheduled, error);
                    }
                    return null;
                });
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(CachedJob.class);
}
