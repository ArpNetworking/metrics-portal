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
package com.arpnetworking.metrics.portal.scheduling.impl;

import com.arpnetworking.metrics.portal.scheduling.JobRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.Maps;
import com.google.common.primitives.Longs;
import com.google.inject.Inject;
import models.internal.scheduling.Job;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple in-memory {@link JobRepository}. Not in any way persistent, probably not good for production usage.
 *
 * @author Spencer Pearson
 */
public final class MapJobRepository implements JobRepository {

    /**
     * Guice constructor.
     */
    @Inject
    public MapJobRepository() {}

    private final AtomicBoolean _open = new AtomicBoolean();
    private final AtomicLong _nonce = new AtomicLong(0);
    private final Map<UUID, Job> _map = Maps.newHashMap();

    @Override
    public void open() {
        assertIsClosed();
        LOGGER.debug().setMessage("opening JobRepository").log();
        _open.set(true);
    }

    @Override
    public void close() {
        LOGGER.debug().setMessage("closing JobRepository").log();
        assertIsOpen();
    }

    @Override
    public UUID add(final Job j) {
        assertIsOpen();
        final UUID id = UUID.nameUUIDFromBytes(Longs.toByteArray(_nonce.getAndIncrement()));
        _map.put(id, j);
        LOGGER.debug()
                .setMessage("created job")
                .addData("id", id)
                .addData("job", j)
                .log();
        return id;
    }

    @Override
    public Optional<Job> get(final UUID id) {
        assertIsOpen();
        return Optional.ofNullable(_map.get(id));
    }

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsClosed() {
        assertIsOpen(false);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_open.get() != expectedState) {
            throw new IllegalStateException("MapJobRepository is not " + (expectedState ? "open" : "closed"));
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MapJobRepository.class);

}
