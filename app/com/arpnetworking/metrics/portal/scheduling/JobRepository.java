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

import models.internal.scheduling.Job;

import java.util.Optional;
import java.util.UUID;

/**
 * A storage medium for {@link Job}s. Essentially a Map that mints unique keys for new Job values.
 *
 * @author Spencer Pearson
 */
public interface JobRepository {

    /**
     * Open / connect to the repository. Must be called before any other methods.
     */
    void open();

    /**
     * Close the repository. Any further operations are illegal until the next open() call.
     */
    void close();

    /**
     * Add a new Job to the repository.
     *
     * @param j The Job to record.
     * @return A unique identifier for that job.
     */
    UUID add(Job j);

    /**
     * Retrieve a previously-stored Job.
     *
     * @param id The id assigned to the Job by a previous call to {@code add}.
     * @return The Job stored with that key.
     */
    Optional<Job> get(UUID id);
}
