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
package com.arpnetworking.metrics.portal.scheduling;

import models.internal.Organization;
import models.internal.scheduling.Job;
import models.internal.scheduling.JobExecution;

import java.io.IOException;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * A storage medium for {@link JobExecution}s.
 *
 * @param <T> The type of result produced by the {@link Job}s.
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public interface JobExecutionRepository<T> {

    /**
     * Open / connect to the repository. Must be called before any other methods.
     */
    void open();

    /**
     * Close the repository. Any further operations are illegal until the next open() call.
     */
    void close();

    /**
     * Get the last successful execution, if any.
     *
     * @param jobId The UUID of the job that completed.
     * @param organization The organization owning the job.
     * @throws NoSuchElementException if no job has the given UUID.
     * @throws IOException if deserializing the job result fails.
     * @return The last successful execution.
     */
    Optional<JobExecution.Success<T>> getLastSuccess(UUID jobId, Organization organization) throws NoSuchElementException, IOException;

    /**
     * Get the last completed execution, regardless of if it succeeded.
     *
     * @param jobId The UUID of the job that completed.
     * @param organization The organization owning the job.
     * @throws NoSuchElementException if no job has the given UUID.
     * @throws IOException if deserializing a job result fails.
     * @return The last completed execution.
     */
    Optional<JobExecution<T>> getLastCompleted(UUID jobId, Organization organization) throws NoSuchElementException, IOException;

    /**
     * Notify the repository that a job has started executing.
     *
     * @param jobId The UUID of the job that completed.
     * @param organization The organization owning the job.
     * @param scheduled The time that the job started running for.
     */
    void jobStarted(UUID jobId, Organization organization, Instant scheduled);

    /**
     * Notify the repository that a job finished executing successfully.
     *
     * @param jobId The UUID of the job that completed.
     * @param organization The organization owning the job.
     * @param scheduled The time that the completed job-run was scheduled for.
     * @param result The result that the job computed.
     */
    void jobSucceeded(UUID jobId, Organization organization, Instant scheduled, T result);

    /**
     * Notify the repository that a job encountered an error and aborted execution.
     *
     * @param jobId The UUID of the job that failed.
     * @param organization The organization owning the job.
     * @param scheduled The time that the failed job-run was scheduled for.
     * @param error The exception that caused the job to fail.
     */
    void jobFailed(UUID jobId, Organization organization, Instant scheduled, Throwable error);
}
