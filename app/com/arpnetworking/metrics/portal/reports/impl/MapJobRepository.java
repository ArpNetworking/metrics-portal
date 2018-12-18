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
package com.arpnetworking.metrics.portal.reports.impl;

import com.arpnetworking.metrics.portal.reports.Job;
import com.arpnetworking.metrics.portal.reports.JobRepository;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
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
    private final Map<String, Job> _specs = Maps.newConcurrentMap();
    @Override
    public @Nullable
    Job get(final String id) {
        return _specs.get(id);
    }

    @Override
    public Stream<String> listSpecs() {
        return _specs.keySet().stream();
    }

    @Override
    public String add(final Job spec) {
        final String id = Long.toString(_nonce.getAndIncrement());
        _specs.put(id, spec);
        return id;
    }

    @Override
    public void open() {

    }

    @Override
    public void close() {

    }
}
