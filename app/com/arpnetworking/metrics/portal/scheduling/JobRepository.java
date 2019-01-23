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
import models.internal.scheduling.Job;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

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
     * Create or update a {@link Job}.
     *
     * @param job The {@link Job} to create or update.
     * @param organization The organization owning the job.
     */
    void addOrUpdateJob(Job<T> job, Organization organization);

    /**
     * Retrieve a previously-stored Job.
     *
     * @param id The id assigned to the Job by a previous call to {@code add}.
     * @param organization The organization owning the job.
     * @return The Job stored with that key.
     */
    Optional<Job<T>> getJob(UUID id, Organization organization);

    /**
     * Get the last time that a job with a given UUID was run.
     *
     * @param id The id assigned to the Job by a previous call to {@code add}.
     * @param organization The organization owning the job.
     * @return The last time that that job was executed.
     * @throws NoSuchElementException if no job has the given UUID.
     */
    Optional<Instant> getLastRun(UUID id, Organization organization) throws NoSuchElementException;

    /**
     * Returns every job the repository contains (for a given {@link Organization}).
     *
     * @param organization The organization whose jobs to iterate over.
     * @return A stream of every job in the repository. No order guaranteed.
     *         No guarantees about presence of jobs added by other threads/processes while the stream is running.
     */
    Stream<Job<T>> getAllJobs(Organization organization);

    /**
     * Notify the repository that a job has started executing.
     *
     * @param id The UUID of the job that completed.
     * @param organization The organization owning the job.
     * @param scheduled The time that the job started running for.
     */
    void jobStarted(UUID id, Organization organization, Instant scheduled);

    /**
     * Notify the repository that a job finished executing successfully.
     *
     * @param id The UUID of the job that completed.
     * @param organization The organization owning the job.
     * @param scheduled The time that the completed job-run was scheduled for.
     * @param result The result that the job computed.
     */
    void jobSucceeded(UUID id, Organization organization, Instant scheduled, T result);

    /**
     * Notify the repository that a job encountered an error and aborted execution.
     *
     * @param id The UUID of the job that failed.
     * @param organization The organization owning the job.
     * @param scheduled The time that the failed job-run was scheduled for.
     * @param error The exception that caused the job to fail.
     */
    void jobFailed(UUID id, Organization organization, Instant scheduled, Throwable error);

}
