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

import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.scheduling.Job;

import java.util.Optional;
import java.util.UUID;

/**
 * A storage medium for {@link Job}s.
 *
 * @param <T> The type of result produced by the {@link Job}s.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public interface JobRepository<T> {

    /**
     * Open / connect to the repository. Must be called before any other methods.
     */
    void open();

    /**
     * Close the repository. Any further operations are illegal until the next open() call.
     */
    void close();

    /**
     * Retrieve a previously-stored Job.
     *
     * @param id The id assigned to the Job by a previous call to {@code add}.
     * @param organization The organization owning the job.
     * @return The Job stored with that key.
     */
    Optional<Job<T>> getJob(UUID id, Organization organization);

    /**
     * Create a job query against this repository.
     *
     * @param organization Organization to search in.
     * @return An instance of {@code JobQuery}.
     */
    JobQuery<T> createJobQuery(Organization organization);

    /**
     * Query jobs.
     *
     * @param query The {@code JobQuery} instance to execute.
     * @return The jobs resulting from executing the query.
     */
    QueryResult<Job<T>> queryJobs(JobQuery<T> query);
}
