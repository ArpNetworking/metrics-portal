package com.arpnetworking.metrics.portal.integration.repositories;

import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.integration.test.EbeanServerHelper;
import com.arpnetworking.metrics.portal.reports.impl.DatabaseReportExecutionRepository;
import com.google.common.base.Throwables;
import io.ebean.EbeanServer;
import models.internal.Organization;
import models.internal.impl.DefaultReportResult;
import models.internal.reports.Report;
import models.internal.scheduling.JobExecution;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DatabaseReportExecutionRepositoryIT {
    private DatabaseReportExecutionRepository _repository;
    private Organization _organization;
    private UUID _reportId;

    @Before
    public void setUp() {
        final EbeanServer _server = EbeanServerHelper.getMetricsDatabase();
        _repository = new DatabaseReportExecutionRepository(_server);
        _repository.open();

        final models.ebean.Organization _ebeanOrganization = TestBeanFactory.createEbeanOrganization();
        _server.save(_ebeanOrganization);
        _organization = TestBeanFactory.organizationFrom(_ebeanOrganization);

        final models.ebean.Report ebeanReport = TestBeanFactory.createEbeanReport(_ebeanOrganization);
        _reportId = ebeanReport.getUuid();
        // TODO(cbriones): I'm not sure why schedule / source need to be explicitly saved; I would expect a cascade to occur.
        _server.save(ebeanReport.getSchedule());
        _server.save(ebeanReport.getReportSource());
        _server.save(ebeanReport);
    }

    @After
    public void tearDown() {
        _repository.close();
    }

    @Test
    public void testJobStarted() {
        final Instant scheduled = Instant.now();

        _repository.jobStarted(_reportId, _organization, scheduled);

        final Optional<JobExecution<Report.Result>> executionResult = _repository.getLastScheduled(_reportId, _organization);

        assertTrue(executionResult.isPresent());
        final JobExecution<Report.Result> execution = executionResult.get();

        assertThat(execution.getJobId(), equalTo(_reportId));
        assertThat(execution.getScheduled(), equalTo(scheduled));

        assertThat(_repository.getLastCompleted(_reportId, _organization), equalTo(Optional.empty()));

        (new JobExecution.Visitor<Report.Result, Void>() {
            @Override
            public Void visit(final JobExecution.Success<Report.Result> state) {
                fail("Got a success state when expecting started.");
                return null;
            }

            @Override
            public Void visit(final JobExecution.Failure<Report.Result> state) {
                fail("Got a failure state when expecting started.");
                return null;
            }

            @Override
            public Void visit(final JobExecution.Started<Report.Result> state) {
                assertThat(state.getStartedAt(), not(nullValue()));
                return null;
            }
        }).visit(execution);
    }

    @Test
    public void testJobSucceeded() {
        final Report.Result result = newResult();
        final Instant scheduled = Instant.now();

        _repository.jobStarted(_reportId, _organization, scheduled);
        _repository.jobSucceeded(_reportId, _organization, scheduled, result);

        final Optional<JobExecution.Success<Report.Result>> executionResult = _repository.getLastSuccess(_reportId, _organization);

        assertTrue(executionResult.isPresent());

        final JobExecution.Success<Report.Result> execution = executionResult.get();
        assertThat(execution.getCompletedAt(), not(nullValue()));
        assertThat(execution.getJobId(), equalTo(_reportId));
        assertThat(execution.getStartedAt(), not(nullValue()));
        assertThat(execution.getScheduled(), equalTo(scheduled));
        assertThat(execution.getResult(), not(nullValue()));

        // If we get the last completed run, it should retrieve the same execution.

        final Optional<JobExecution<Report.Result>> lastRun = _repository.getLastCompleted(_reportId, _organization);
        assertThat(lastRun, not(equalTo(Optional.empty())));

        (new JobExecution.Visitor<Report.Result, Void>() {
            @Override
            public Void visit(final JobExecution.Success<Report.Result> state) {
                assertThat(state.getCompletedAt(), not(nullValue()));
                assertThat(state.getJobId(), equalTo(_reportId));
                assertThat(state.getStartedAt(), not(nullValue()));
                assertThat(state.getScheduled(), equalTo(scheduled));
                assertThat(state.getResult(), not(nullValue()));
                return null;
            }

            @Override
            public Void visit(final JobExecution.Failure<Report.Result> state) {
                fail("Got a failure state when expecting success.");
                return null;
            }

            @Override
            public Void visit(final JobExecution.Started<Report.Result> state) {
                fail("Got a started state when expecting success.");
                return null;
            }
        }).visit(lastRun.get());
    }

    @Test
    public void testJobFailed() {
        final Instant scheduled = Instant.now();
        final Throwable error = new RuntimeException("something went wrong.");

        _repository.jobStarted(_reportId, _organization, scheduled);
        _repository.jobFailed(_reportId, _organization, scheduled, error);

        final Optional<JobExecution<Report.Result>> lastRun = _repository.getLastCompleted(_reportId, _organization);
        assertThat(lastRun, not(equalTo(Optional.empty())));

        (new JobExecution.Visitor<Report.Result, Void>() {
            @Override
            public Void visit(final JobExecution.Success<Report.Result> state) {
                fail("Got a success state when expecting failure.");
                return null;
            }

            @Override
            public Void visit(final JobExecution.Failure<Report.Result> state) {
                final Throwable retrievedError = state.getError();
                assertThat(state.getCompletedAt(), not(nullValue()));
                assertThat(state.getJobId(), equalTo(_reportId));
                assertThat(state.getStartedAt(), not(nullValue()));
                assertThat(state.getScheduled(), equalTo(scheduled));
                assertThat(retrievedError.getMessage(), equalTo(Throwables.getStackTraceAsString(error)));
                return null;
            }

            @Override
            public Void visit(final JobExecution.Started<Report.Result> state) {
                fail("Got a started state when expecting failure.");
                return null;
            }
        }).visit(lastRun.get());
    }

    @Test
    public void testJobMultipleRuns() {
        final Instant t0 = Instant.now();
        final Duration dt = Duration.ofDays(1);

        final int NUM_JOBS = 4;
        for (int i = 0; i < NUM_JOBS; i++) {
            _repository.jobStarted(_reportId, _organization, t0.plus(dt.multipliedBy(i)));
        }

        _repository.jobFailed(_reportId, _organization, t0.plus(dt.multipliedBy(0)), new IllegalStateException());
        _repository.jobFailed(_reportId, _organization, t0.plus(dt.multipliedBy(1)), new IllegalStateException());
        _repository.jobSucceeded(_reportId, _organization, t0.plus(dt.multipliedBy(2)), newResult());
        _repository.jobSucceeded(_reportId, _organization, t0.plus(dt.multipliedBy(3)), newResult());

        assertEquals(
                t0.plus(dt.multipliedBy(3)),
                _repository.getLastCompleted(_reportId, _organization).get().getScheduled()
        );
    }

    @Test
    public void testStateChange() {
        final Report.Result result = newResult();
        final Instant scheduled = Instant.now();
        final Throwable error = new RuntimeException("something went wrong.");

        _repository.jobStarted(_reportId, _organization, scheduled);
        _repository.jobSucceeded(_reportId, _organization, scheduled, result);

        final JobExecution.Success<Report.Result> execution = _repository.getLastSuccess(_reportId, _organization).get();
        assertThat(execution.getResult(), not(nullValue()));

        // A failed updated should *not* clear the start time but it should clear the result
        _repository.jobFailed(_reportId, _organization, scheduled, error);
        final JobExecution<Report.Result> updatedExecution = _repository.getLastCompleted(_reportId, _organization).get();

        (new JobExecution.Visitor<Report.Result, Void>() {
            @Override
            public Void visit(final JobExecution.Success<Report.Result> state) {
                fail("Got a success state when expecting failure.");
                return null;
            }

            @Override
            public Void visit(final JobExecution.Failure<Report.Result> state) {
                final Throwable retrievedError = state.getError();
                assertThat(state.getCompletedAt(), not(nullValue()));
                assertThat(state.getJobId(), equalTo(_reportId));
                assertThat(state.getStartedAt(), not(nullValue()));
                assertThat(state.getScheduled(), equalTo(scheduled));
                assertThat(retrievedError.getMessage(), equalTo(Throwables.getStackTraceAsString(error)));
                return null;
            }

            @Override
            public Void visit(final JobExecution.Started<Report.Result> state) {
                fail("Got a started state when expecting failure.");
                return null;
            }
        }).visit(updatedExecution);
    }

    private Report.Result newResult() {
        return new DefaultReportResult();
    }
}
