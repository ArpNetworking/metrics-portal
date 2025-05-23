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

package com.arpnetworking.metrics.portal.integration.repositories;

import com.arpnetworking.commons.java.util.concurrent.CompletableFutures;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.scheduling.JobExecutionRepository;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import models.internal.Organization;
import models.internal.scheduling.JobExecution;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Generic integration tests for implementations of {@link JobExecutionRepository}.
 *
 * @param <T> The job result type.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public abstract class JobExecutionRepositoryIT<T> {
    private JobExecutionRepository<T> _repository;
    private UUID _jobId;
    private Organization _organization;

    /**
     * Construct an arbitrary result to be used in a test case.
     *
     * @return The result.
     */
    abstract T newResult();

    /**
     * Construct a <b>closed</b> instance of the Repository to be tested.
     *
     * @return The repository.
     */
    abstract JobExecutionRepository<T> setUpRepository(Organization organization);

    /**
     * Ensure that a job with the given id is valid.
     * <p>
     * The organization and job id for the given test case are provided as some repositories may validate
     * that these objects exist when fetching the associated executions. Each test suite may want to ensure
     * that these IDs reference valid objects before the test is run.
     */
    abstract void ensureJobExists(Organization organization, UUID jobId);

    @Before
    public void setUpRepository() {
        _jobId = UUID.randomUUID();
        _organization = TestBeanFactory.createOrganization();
        _repository = setUpRepository(_organization);
        ensureJobExists(_organization, _jobId);
        _repository.open();
    }

    @After
    public void tearDown() {
        _repository.close();
    }

    @Test
    public void testJobStarted() throws Exception {
        final Instant scheduled = Instant.now().truncatedTo(ChronoUnit.MICROS);

        _repository.jobStarted(_jobId, _organization, scheduled)
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        final Optional<JobExecution<T>> executionResult = _repository.getLastScheduled(_jobId, _organization)
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        assertTrue(executionResult.isPresent());
        final JobExecution<T> execution = executionResult.get();

        assertThat(execution.getJobId(), equalTo(_jobId));
        assertThat(execution.getScheduled(), equalTo(scheduled));

        assertThat(_repository.getLastCompleted(_jobId, _organization)
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS), equalTo(Optional.empty()));

        // TODO(cbriones): This doesn't actually require an integer, but spotbugs complains that we're returning null if we use Void.
        // Of course, in that case there's nothing else we can possibly return. The visitors below should also be changed.
        (new JobExecution.Visitor<T, Integer>() {
            @Override
            public Integer visit(final JobExecution.Success<T> state) {
                fail("Got a success state when expecting started.");
                return 0;
            }

            @Override
            public Integer visit(final JobExecution.Failure<T> state) {
                fail("Got a failure state when expecting started.");
                return 0;
            }

            @Override
            public Integer visit(final JobExecution.Started<T> state) {
                assertThat(state.getStartedAt(), not(nullValue()));
                return 0;
            }
        }).apply(execution);
    }

    @Test
    public void testJobSucceeded() throws Exception {
        final T result = newResult();
        final Instant scheduled = Instant.now().truncatedTo(ChronoUnit.MICROS);

        final Optional<JobExecution.Success<T>> executionResult =
            _repository.jobStarted(_jobId, _organization, scheduled)
                .thenCompose(ignored -> _repository.jobSucceeded(_jobId, _organization, scheduled, result))
                .thenCompose(ignored -> _repository.getLastSuccess(_jobId, _organization))
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        assertTrue(executionResult.isPresent());

        final JobExecution.Success<T> execution = executionResult.get();
        assertThat(execution.getCompletedAt(), not(nullValue()));
        assertThat(execution.getJobId(), equalTo(_jobId));
        assertThat(execution.getStartedAt(), not(nullValue()));
        assertThat(execution.getScheduled(), equalTo(scheduled));
        assertThat(execution.getResult(), not(nullValue()));

        // If we get the last completed run, it should retrieve the same execution.

        final Optional<JobExecution<T>> lastRun = _repository.getLastCompleted(_jobId, _organization)
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        assertThat(lastRun, not(equalTo(Optional.empty())));

        (new JobExecution.Visitor<T, Integer>() {
            @Override
            public Integer visit(final JobExecution.Success<T> state) {
                assertThat(state.getCompletedAt(), not(nullValue()));
                assertThat(state.getJobId(), equalTo(_jobId));
                assertThat(state.getStartedAt(), not(nullValue()));
                assertThat(state.getScheduled(), equalTo(scheduled));
                assertThat(state.getResult(), not(nullValue()));
                return 0;
            }

            @Override
            public Integer visit(final JobExecution.Failure<T> state) {
                fail("Got a failure state when expecting success.");
                return 0;
            }

            @Override
            public Integer visit(final JobExecution.Started<T> state) {
                fail("Got a started state when expecting success.");
                return 0;
            }
        }).apply(lastRun.get());
    }

    @Test
    public void testJobScheduledInThePast() throws Exception {
        final T result = newResult();
        final Instant scheduled = Instant.parse("2019-01-01T00:00:00Z");

        final Optional<JobExecution.Success<T>> lastRun =
                _repository.jobStarted(_jobId, _organization, scheduled)
                        .thenCompose(ignored -> _repository.jobSucceeded(_jobId, _organization, scheduled, result))
                        .thenCompose(ignore -> _repository.getLastSuccess(_jobId, _organization))
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);

        if (!lastRun.isPresent()) {
            fail("Expected a non-empty success to be returned.");
        }
        assertThat(lastRun.get().getScheduled(), is(scheduled));
        assertThat(lastRun.get().getResult(), is(result));
    }

    @Test
    public void testJobFailed() throws Exception {
        final Instant scheduled = Instant.now().truncatedTo(ChronoUnit.MICROS);
        final Throwable error = new RuntimeException("something went wrong.");

        final Optional<JobExecution<T>> lastRun =
                _repository.jobStarted(_jobId, _organization, scheduled)
                        .thenCompose(ignored -> _repository.jobFailed(_jobId, _organization, scheduled, error))
                        .thenCompose(ignore -> _repository.getLastCompleted(_jobId, _organization))
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);

        assertThat(lastRun, not(equalTo(Optional.empty())));

        (new JobExecution.Visitor<T, Integer>() {
            @Override
            public Integer visit(final JobExecution.Success<T> state) {
                fail("Got a success state when expecting failure.");
                return 0;
            }

            @Override
            public Integer visit(final JobExecution.Failure<T> state) {
                final Throwable retrievedError = state.getError();
                assertThat(state.getCompletedAt(), not(nullValue()));
                assertThat(state.getJobId(), equalTo(_jobId));
                assertThat(state.getStartedAt(), not(nullValue()));
                assertThat(state.getScheduled(), equalTo(scheduled));
                assertThat(retrievedError.getMessage(), equalTo(Throwables.getStackTraceAsString(error)));
                return 0;
            }

            @Override
            public Integer visit(final JobExecution.Started<T> state) {
                fail("Got a started state when expecting failure.");
                return 0;
            }
        }).apply(lastRun.get());
    }

    @Test
    public void testJobMultipleRuns() throws Exception {
        final Instant t0 = Instant.now().truncatedTo(ChronoUnit.MICROS);
        final Duration dt = Duration.ofHours(1);

        final List<CompletionStage<Void>> markedStarted = new ArrayList<>();
        final int numJobs = 4;
        for (int i = 0; i < numJobs; i++) {
            // These can be run in parallel since they're different entries.
            markedStarted.add(_repository.jobStarted(_jobId, _organization, t0.plus(dt.multipliedBy(i))));
        }
        CompletableFutures.allOf(markedStarted)
            .thenCompose(ignore -> _repository.jobFailed(_jobId, _organization, t0.plus(dt.multipliedBy(0)), new IllegalStateException()))
            .thenCompose(ignore -> _repository.jobFailed(_jobId, _organization, t0.plus(dt.multipliedBy(1)), new IllegalStateException()))
            .thenCompose(ignore -> _repository.jobSucceeded(_jobId, _organization, t0.plus(dt.multipliedBy(2)), newResult()))
            .thenCompose(ignore -> _repository.jobSucceeded(_jobId, _organization, t0.plus(dt.multipliedBy(3)), newResult()))
            .get(10, TimeUnit.SECONDS);

        assertEquals(
                t0.plus(dt.multipliedBy(3)),
                _repository.getLastCompleted(_jobId, _organization)
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS)
                        .get()
                        .getScheduled()
        );
    }

    @Test
    public void testSanityCheckRunningMultipleJobsWithDistinctResults() throws Exception {
        final T firstResult = newResult();
        final T secondResult = newResult();
        final Instant scheduled = Instant.now().truncatedTo(ChronoUnit.MICROS);

        JobExecution.Success<T> execution =
                _repository.jobStarted(_jobId, _organization, scheduled)
                        .thenCompose(ignored -> _repository.jobSucceeded(_jobId, _organization, scheduled, firstResult))
                        .thenCompose(ignore -> _repository.getLastSuccess(_jobId, _organization))
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS)
                        .get();

        assertThat(execution.getResult(), is(firstResult));

        final UUID secondJob = UUID.randomUUID();
        ensureJobExists(_organization, secondJob);

        _repository.jobStarted(secondJob, _organization, scheduled);
        _repository.jobSucceeded(secondJob, _organization, scheduled, secondResult);

        execution =
                _repository.jobStarted(_jobId, _organization, scheduled)
                        .thenCompose(ignored -> _repository.jobSucceeded(_jobId, _organization, scheduled, secondResult))
                        .thenCompose(ignore -> _repository.getLastSuccess(_jobId, _organization))
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS)
                        .get();

        assertThat(execution.getResult(), is(secondResult));
    }

    @Test
    public void testStateChange() throws Exception {
        final T result = newResult();
        final Instant scheduled = Instant.now().truncatedTo(ChronoUnit.MICROS);
        final Throwable error = new RuntimeException("something went wrong.");

        _repository.jobStarted(_jobId, _organization, scheduled)
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        _repository.jobSucceeded(_jobId, _organization, scheduled, result)
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        final JobExecution.Success<T> execution = _repository.getLastSuccess(_jobId, _organization)
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS)
                .get();
        assertThat(execution.getResult(), not(nullValue()));

        // A failed updated should *not* clear the start time but it should clear the result
        _repository.jobFailed(_jobId, _organization, scheduled, error)
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        final JobExecution<T> updatedExecution = _repository.getLastCompleted(_jobId, _organization)
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS)
                .get();

        (new JobExecution.Visitor<T, Integer>() {
            @Override
            public Integer visit(final JobExecution.Success<T> state) {
                fail("Got a success state when expecting failure.");
                return 0;
            }

            @Override
            public Integer visit(final JobExecution.Failure<T> state) {
                final Throwable retrievedError = state.getError();
                assertThat(state.getCompletedAt(), not(nullValue()));
                assertThat(state.getJobId(), equalTo(_jobId));
                assertThat(state.getStartedAt(), not(nullValue()));
                assertThat(state.getScheduled(), equalTo(scheduled));
                assertThat(retrievedError.getMessage(), equalTo(Throwables.getStackTraceAsString(error)));
                return 0;
            }

            @Override
            public Integer visit(final JobExecution.Started<T> state) {
                fail("Got a started state when expecting failure.");
                return 0;
            }
        }).apply(updatedExecution);
    }

    @Test
    public void testGetLastSuccessBatch() throws Exception {
        final int runsPerJob = 3;
        final int numJobs = 5;

        final List<UUID> existingJobIds = new ArrayList<>();
        for (int i = 0; i < numJobs; i++) {
            final UUID jobId = UUID.randomUUID();
            ensureJobExists(_organization, jobId);
            existingJobIds.add(jobId);
        }
        final Instant truncatedNow = Instant.now().truncatedTo(ChronoUnit.DAYS);

        // Create several jobs, each with several runs.
        for (final UUID jobId : existingJobIds) {
            for (int i = 0; i < runsPerJob; i++) {
                final T result = newResult();
                final Instant scheduled = truncatedNow.minus(Duration.ofDays(runsPerJob - 1 - i));
                _repository.jobStarted(jobId, _organization, scheduled)
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);
                _repository.jobSucceeded(jobId, _organization, scheduled, result)
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);
            }
        }
        // Create a failed execution in the "future" for one job id
        final Instant futureRun = truncatedNow.plus(Duration.ofDays(1));
        _repository.jobStarted(existingJobIds.get(0), _organization, futureRun);
        _repository.jobFailed(existingJobIds.get(0), _organization, futureRun, new Throwable("an error"));

        // Create an additional job that we don't care about.
        final UUID extraJobId = UUID.randomUUID();
        ensureJobExists(_organization, extraJobId);
        _repository.jobStarted(extraJobId, _organization, truncatedNow)
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        _repository.jobSucceeded(extraJobId, _organization, truncatedNow, newResult())
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        // Create an additional job with a failure.
        final UUID failedJobId = UUID.randomUUID();
        ensureJobExists(_organization, failedJobId);
        _repository.jobStarted(extraJobId, _organization, truncatedNow)
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        _repository.jobFailed(extraJobId, _organization, truncatedNow, new Throwable("an error"))
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        // Request an ID that doesn't exist.
        final UUID nonexistentId = UUID.randomUUID();
        final ImmutableList<UUID> jobIds = new ImmutableList.Builder<UUID>()
                .addAll(existingJobIds)
                .add(nonexistentId)
                .add(failedJobId)
                .build();

        final LocalDate currentDate = ZonedDateTime.ofInstant(truncatedNow, ZoneOffset.UTC).toLocalDate();
        final Map<UUID, JobExecution.Success<T>> successes =
                _repository.getLastSuccessBatch(jobIds, _organization, currentDate.minusDays(runsPerJob))
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);

        for (final UUID jobId : existingJobIds) {
            assertThat(successes, hasKey(jobId));
            assertThat(successes.get(jobId).getScheduled(), is(truncatedNow));
        }
        assertThat("did not expect extra job id", successes, not(hasKey(extraJobId)));
        assertThat("did not expect a result for nonexistent id", successes, not(hasKey(nonexistentId)));
        assertThat("did not a expect a result for a failed job", successes, not(hasKey(failedJobId)));
    }
}
