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

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
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
     *
     * @return The repository.
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
    @SuppressFBWarnings(
            value = "SIC_INNER_SHOULD_BE_STATIC_ANON",
            justification = "The 'this' reference is the test class and I'm not concerned about it potentially living too long."
    )
    public void testJobStarted() {
        final Instant scheduled = Instant.now();

        _repository.jobStarted(_jobId, _organization, scheduled);

        final Optional<JobExecution<T>> executionResult = _repository.getLastScheduled(_jobId, _organization);

        assertTrue(executionResult.isPresent());
        final JobExecution<T> execution = executionResult.get();

        assertThat(execution.getJobId(), equalTo(_jobId));
        assertThat(execution.getScheduled(), equalTo(scheduled));

        assertThat(_repository.getLastCompleted(_jobId, _organization), equalTo(Optional.empty()));

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
    public void testJobSucceeded() {
        final T result = newResult();
        final Instant scheduled = Instant.now();

        _repository.jobStarted(_jobId, _organization, scheduled);
        _repository.jobSucceeded(_jobId, _organization, scheduled, result);

        final Optional<JobExecution.Success<T>> executionResult = _repository.getLastSuccess(_jobId, _organization);

        assertTrue(executionResult.isPresent());

        final JobExecution.Success<T> execution = executionResult.get();
        assertThat(execution.getCompletedAt(), not(nullValue()));
        assertThat(execution.getJobId(), equalTo(_jobId));
        assertThat(execution.getStartedAt(), not(nullValue()));
        assertThat(execution.getScheduled(), equalTo(scheduled));
        assertThat(execution.getResult(), not(nullValue()));

        // If we get the last completed run, it should retrieve the same execution.

        final Optional<JobExecution<T>> lastRun = _repository.getLastCompleted(_jobId, _organization);
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
    public void testJobScheduledInThePast() {
        final T result = newResult();
        final Instant scheduled = Instant.parse("2019-01-01T00:00:00Z");

        _repository.jobStarted(_jobId, _organization, scheduled);
        _repository.jobSucceeded(_jobId, _organization, scheduled, result);

        final Optional<JobExecution.Success<T>> lastRun = _repository.getLastSuccess(_jobId, _organization);
        if (!lastRun.isPresent()) {
            fail("Expected a non-empty success to be returned.");
        }
        assertThat(lastRun.get().getScheduled(), is(scheduled));
        assertThat(lastRun.get().getResult(), is(result));
    }

    @Test
    public void testJobFailed() {
        final Instant scheduled = Instant.now();
        final Throwable error = new RuntimeException("something went wrong.");

        _repository.jobStarted(_jobId, _organization, scheduled);
        _repository.jobFailed(_jobId, _organization, scheduled, error);

        final Optional<JobExecution<T>> lastRun = _repository.getLastCompleted(_jobId, _organization);
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
    public void testJobMultipleRuns() {
        final Instant t0 = Instant.now();
        final Duration dt = Duration.ofHours(1);

        final int numJobs = 4;
        for (int i = 0; i < numJobs; i++) {
            _repository.jobStarted(_jobId, _organization, t0.plus(dt.multipliedBy(i)));
        }

        _repository.jobFailed(_jobId, _organization, t0.plus(dt.multipliedBy(0)), new IllegalStateException());
        _repository.jobFailed(_jobId, _organization, t0.plus(dt.multipliedBy(1)), new IllegalStateException());
        _repository.jobSucceeded(_jobId, _organization, t0.plus(dt.multipliedBy(2)), newResult());
        _repository.jobSucceeded(_jobId, _organization, t0.plus(dt.multipliedBy(3)), newResult());

        assertEquals(
                t0.plus(dt.multipliedBy(3)),
                _repository.getLastCompleted(_jobId, _organization).get().getScheduled()
        );
    }

    @Test
    public void testSanityCheckRunningMultipleJobsWithDistinctResults() {
        final T firstResult = newResult();
        final T secondResult = newResult();
        final Instant scheduled = Instant.now();

        _repository.jobStarted(_jobId, _organization, scheduled);
        _repository.jobSucceeded(_jobId, _organization, scheduled, firstResult);

        JobExecution.Success<T> execution = _repository.getLastSuccess(_jobId, _organization).get();
        assertThat(execution.getResult(), is(firstResult));

        final UUID secondJob = UUID.randomUUID();
        ensureJobExists(_organization, secondJob);

        _repository.jobStarted(secondJob, _organization, scheduled);
        _repository.jobSucceeded(secondJob, _organization, scheduled, secondResult);

        execution = _repository.getLastSuccess(secondJob, _organization).get();
        assertThat(execution.getResult(), is(secondResult));
    }

    @Test
    public void testStateChange() {
        final T result = newResult();
        final Instant scheduled = Instant.now();
        final Throwable error = new RuntimeException("something went wrong.");

        _repository.jobStarted(_jobId, _organization, scheduled);
        _repository.jobSucceeded(_jobId, _organization, scheduled, result);

        final JobExecution.Success<T> execution = _repository.getLastSuccess(_jobId, _organization).get();
        assertThat(execution.getResult(), not(nullValue()));

        // A failed updated should *not* clear the start time but it should clear the result
        _repository.jobFailed(_jobId, _organization, scheduled, error);
        final JobExecution<T> updatedExecution = _repository.getLastCompleted(_jobId, _organization).get();

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
    public void testGetLastSuccessBatch() {
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
                _repository.jobStarted(jobId, _organization, scheduled);
                _repository.jobSucceeded(jobId, _organization, scheduled, result);
            }
        }
        // Create an additional job that we don't care about.
        final UUID extraJobId = UUID.randomUUID();
        ensureJobExists(_organization, extraJobId);
        final Instant extraScheduled = Instant.now().truncatedTo(ChronoUnit.DAYS);
        _repository.jobStarted(extraJobId, _organization, extraScheduled);
        _repository.jobSucceeded(extraJobId, _organization, extraScheduled, newResult());

        // Request an ID that doesn't exist.
        final UUID nonexistentId = UUID.randomUUID();
        final ImmutableList<UUID> jobIds = new ImmutableList.Builder<UUID>()
                .addAll(existingJobIds)
                .add(nonexistentId)
                .build();

        final LocalDate currentDate = ZonedDateTime.ofInstant(truncatedNow, ZoneOffset.UTC).toLocalDate();
        Map<UUID, JobExecution.Success<T>> successes =
                _repository.getLastSuccessBatch(jobIds, _organization, currentDate.minusDays(runsPerJob));
        for (final UUID jobId : existingJobIds) {
            assertThat(successes, hasKey(jobId));
            assertThat(successes.get(jobId).getScheduled(), is(truncatedNow));
        }
        assertThat("did not expect extra job id", successes, not(hasKey(extraJobId)));
        assertThat("did not expect a result for nonexistent id", successes, not(hasKey(nonexistentId)));

        successes = _repository.getLastSuccessBatch(jobIds, _organization, currentDate.plusDays(1));
        assertThat(successes.entrySet(), empty());
    }
}
