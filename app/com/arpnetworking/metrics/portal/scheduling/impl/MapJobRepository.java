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

import models.internal.scheduling.Job;
import com.arpnetworking.metrics.portal.scheduling.JobRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

/**
 * A simple in-memory {@link JobRepository}. Not in any way persistent.
 *
 * @author Spencer Pearson
 */
public final class MapJobRepository implements JobRepository {

    /**
     * Guice constructor.
     */
    @Inject
    public MapJobRepository() {}

    private final AtomicLong _nonce = new AtomicLong(0);
    private final Map<String, Job> _map = Maps.newHashMap();

    @Override
    public String add(final Job j) {
        final String id = Long.toString(_nonce.getAndIncrement());
        _map.put(id, j);
        LOGGER.info()
                .setMessage("created job")
                .addData("id", id)
                .addData("job", j)
                .log();
        return id;
    }

    @Nullable
    @Override
    public Job get(final String id) {
        return _map.get(id);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MapJobRepository.class);

}
