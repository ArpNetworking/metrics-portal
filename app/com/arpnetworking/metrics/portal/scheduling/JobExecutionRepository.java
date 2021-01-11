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

import com.arpnetworking.commons.java.util.concurrent.CompletableFutures;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import models.internal.Organization;
import models.internal.scheduling.Job;
import models.internal.scheduling.JobExecution;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

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
     * Get the most recently scheduled execution, if any.
     * <p>
     * This could possibly return an execution that's pending completion.
     *
     * @param jobId The UUID of the job that completed.
     * @param organization The organization owning the job.
     * @return The most recently scheduled execution.
     * @throws NoSuchElementException if no job has the given UUID.
     */
    CompletionStage<Optional<JobExecution<T>>> getLastScheduled(UUID jobId, Organization organization);

    /**
     * Get the last successful execution, if any.
     *
     * @param jobId The UUID of the job that completed.
     * @param organization The organization owning the job.
     * @return The last successful execution.
     * @throws NoSuchElementException if no job has the given UUID.
     */
    CompletionStage<Optional<JobExecution.Success<T>>> getLastSuccess(UUID jobId, Organization organization)
            throws NoSuchElementException;

    /**
     * Get the last successful execution for each ID, if any.
     *
     * It is possible for the returned list to be smaller than the number of IDs
     * given if some jobs have not been executed at query time.
     *
     * The default behavior is identical to repeated calls to `getLastSuccess`, but
     * implementations may specialize this method for performance reasons.
     *
     * @param jobIds The UUIDs of the jobs to fetch.
     * @param organization The organization owning the jobs.
     * @param maxLookback The farthest date (UTC) in the past to check for executions.
     * @return The last successful executions for each job.
     */
    default CompletionStage<ImmutableMap<UUID, JobExecution.Success<T>>> getLastSuccessBatch(
            List<UUID> jobIds,
            Organization organization,
            LocalDate maxLookback
    ) {
        final List<CompletionStage<Optional<JobExecution.Success<T>>>> futures = jobIds.stream()
            .map(id -> getLastSuccess(id, organization))
            .collect(ImmutableList.toImmutableList());

        return CompletableFutures.allOf(futures)
                .thenApply(ignore -> futures.stream()
                        .map(fut -> fut.toCompletableFuture().join())
                        .flatMap(Streams::stream)
                        .collect(ImmutableMap.toImmutableMap(
                                JobExecution::getJobId,
                                execution -> execution
                        )));
    }

    /**
     * Get the last completed execution, regardless of if it succeeded.
     *
     * @param jobId The UUID of the job that completed.
     * @param organization The organization owning the job.
     * @return The last completed execution.
     * @throws NoSuchElementException if no job has the given UUID.
     */
    CompletionStage<Optional<JobExecution<T>>> getLastCompleted(UUID jobId, Organization organization) throws NoSuchElementException;

    /**
     * Notify the repository that a job has started executing.
     *
     * @param jobId The UUID of the job that completed.
     * @param organization The organization owning the job.
     * @param scheduled The time that the job started running for.
     *
     * @return a future that completes when the operation does.
     */
    CompletionStage<Void> jobStarted(UUID jobId, Organization organization, Instant scheduled);

    /**
     * Notify the repository that a job finished executing successfully.
     *
     * @param jobId The UUID of the job that completed.
     * @param organization The organization owning the job.
     * @param scheduled The time that the completed job-run was scheduled for.
     * @param result The result that the job computed.
     *
     * @return a future that completes when the operation does.
     */
    CompletionStage<JobExecution.Success<T>> jobSucceeded(UUID jobId, Organization organization, Instant scheduled, T result);

    /**
     * Notify the repository that a job encountered an error and aborted execution.
     *
     * @param jobId The UUID of the job that failed.
     * @param organization The organization owning the job.
     * @param scheduled The time that the failed job-run was scheduled for.
     * @param error The exception that caused the job to fail.
     *
     * @return a future that completes when the operation does.
     */
    CompletionStage<Void> jobFailed(UUID jobId, Organization organization, Instant scheduled, Throwable error);
}
