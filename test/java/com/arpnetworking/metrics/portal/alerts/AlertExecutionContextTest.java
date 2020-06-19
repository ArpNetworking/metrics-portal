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

package com.arpnetworking.metrics.portal.alerts;

import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.alerts.scheduling.AlertExecutionContext;
import com.arpnetworking.metrics.portal.query.QueryExecutor;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.NeverSchedule;
import com.arpnetworking.utility.test.ResourceHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import models.internal.BoundedMetricsQuery;
import models.internal.MetricsQueryFormat;
import models.internal.MetricsQueryResult;
import models.internal.Organization;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.impl.DefaultAlert;
import models.internal.impl.DefaultMetricsQuery;
import models.internal.impl.DefaultMetricsQueryResult;
import models.internal.impl.DefaultTimeSeriesResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AlertExecutionContext}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@RunWith(Enclosed.class)
public class AlertExecutionContextTest {
    private static final String TEST_METRIC = "test_metric";
    // This is small because the futures here should never actually block.
    private static final long TEST_TIMEOUT_MS = 50;

    private static MetricsQueryResult getTestcase(final String name) throws IOException {
        final Map<String, models.view.MetricsQueryResult> testcases = ResourceHelper.loadResourceAs(
                AlertExecutionContextTest.class,
                "resultTestCases",
                new TypeReference<Map<String, models.view.MetricsQueryResult>>() {
                });

        final models.view.MetricsQueryResult result = testcases.get(name);
        if (result == null) {
            fail("Could not find testcase: " + name);
        }
        return result.toInternal();
    }

    public abstract static class BaseTestCases {
        protected AlertExecutionContext _context;
        protected Alert _alert;
        protected Schedule _schedule;
        protected QueryExecutor _executor;

        @Before
        public void setUp() {
            final Organization organization = TestBeanFactory.createOrganization();
            final UUID id = UUID.randomUUID();
            _schedule = NeverSchedule.getInstance();
            _alert = new DefaultAlert.Builder()
                    .setId(id)
                    .setOrganization(organization)
                    .setEnabled(true)
                    .setName("TestAlert")
                    .setDescription("Used in a test.")
                    .setQuery(
                            new DefaultMetricsQuery.Builder()
                                    .setQuery("This query is invalid but never evaluated")
                                    .setFormat(MetricsQueryFormat.KAIROS_DB)
                                    .build()
                    )
                    .build();
            _executor = Mockito.mock(QueryExecutor.class);
            when(_executor.periodHint(any())).thenReturn(Optional.of(ChronoUnit.HOURS));
            _context = new AlertExecutionContext(
                    _schedule,
                    _executor
            );
        }
    }

    public static final class RegularTests extends BaseTestCases {
        @Test
        public void testReturnsTheConfiguredSchedule() {
            assertThat(_context.getSchedule(_alert), equalTo(_schedule));
        }

        @Test
        public void testQueryExecuteError() throws InterruptedException {
            final Throwable queryError = new RuntimeException("Something went wrong");
            final CompletableFuture<MetricsQueryResult> exceptionalCompletionStage = new CompletableFuture<>();
            exceptionalCompletionStage.completeExceptionally(queryError);
            when(_executor.executeQuery(any())).thenReturn(exceptionalCompletionStage);

            final CompletionStage<AlertEvaluationResult> cs = _context.execute(_alert, Instant.now());

            try {
                cs.toCompletableFuture().get();
                fail("Expected an exception");
            } catch (final ExecutionException e) {
                assertThat(e.getCause(), equalTo(queryError));
            }
        }

        @Test
        public void testDatapointTooOldToBeConsideredFiring() throws Exception {
            final Instant scheduled = Instant.now();
            final ChronoUnit period = ChronoUnit.HOURS;
            when(_executor.periodHint(any())).thenReturn(Optional.of(period));
            final Instant lastDatapointTs = scheduled.minus(1, period);

            final MetricsQueryResult mockResult = new DefaultMetricsQueryResult.Builder()
                    .setQueryResult(new DefaultTimeSeriesResult.Builder()
                            .setQueries(ImmutableList.of(
                                    new DefaultTimeSeriesResult.Query.Builder()
                                            .setSampleSize(1000L)
                                            .setResults(ImmutableList.of(
                                                    new DefaultTimeSeriesResult.Result.Builder()
                                                            .setName(TEST_METRIC)
                                                            .setValues(ImmutableList.of(
                                                                    new DefaultTimeSeriesResult.DataPoint.Builder()
                                                                            .setTime(lastDatapointTs)
                                                                            .setValue(1)
                                                                            .build()
                                                            ))
                                                            .build()
                                            ))
                                            .build()
                            ))
                            .build()
                    )
                    .build();

            when(_executor.executeQuery(any())).thenReturn(CompletableFuture.completedFuture(mockResult));
            final AlertEvaluationResult result = _context.execute(_alert, Instant.now()).toCompletableFuture().get(
                    TEST_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);

            assertThat(result.getName(), equalTo(TEST_METRIC));
            assertThat(result.getFiringTags(), equalTo(ImmutableList.of()));
        }

        @Test(expected = ExecutionException.class)
        public void testMissingPeriodHint() throws Exception {
            when(_executor.periodHint(any())).thenReturn(Optional.empty());
            when(_executor.executeQuery(any())).thenReturn(new CompletableFuture<>());
            _context.execute(_alert, Instant.now()).toCompletableFuture().get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        @Test
        public void testAppliesExpectedTimeRange() {
            final CompletableFuture<MetricsQueryResult> pendingResponse = new CompletableFuture<>();
            final ArgumentCaptor<BoundedMetricsQuery> captor = ArgumentCaptor.forClass(BoundedMetricsQuery.class);
            when(_executor.executeQuery(captor.capture())).thenReturn(pendingResponse);
            when(_executor.periodHint(any())).thenReturn(Optional.of(ChronoUnit.MINUTES));

            // Scheduled for now, minutely

            Instant scheduled = Instant.now();
            _context.execute(_alert, scheduled);

            BoundedMetricsQuery captured = captor.getValue();
            Instant truncatedScheduled = scheduled.truncatedTo(ChronoUnit.MINUTES);

            assertThat(captured.getStartTime().toInstant(), equalTo(truncatedScheduled.minus(Duration.ofMinutes(1))));
            assertThat(captured.getEndTime().map(ZonedDateTime::toInstant), equalTo(Optional.of(truncatedScheduled)));

            // Scheduled for one week ago

            scheduled = Instant.now().minus(Duration.ofDays(7));
            _context.execute(_alert, scheduled);

            captured = captor.getValue();
            truncatedScheduled = scheduled.truncatedTo(ChronoUnit.MINUTES);

            assertThat(captured.getStartTime().toInstant(), equalTo(truncatedScheduled.minus(Duration.ofMinutes(1))));
            assertThat(captured.getEndTime().map(ZonedDateTime::toInstant), equalTo(Optional.of(truncatedScheduled)));

            // Scheduled for now, hourly

            when(_executor.periodHint(any())).thenReturn(Optional.of(ChronoUnit.HOURS));

            scheduled = Instant.now();
            _context.execute(_alert, scheduled);

            captured = captor.getValue();
            truncatedScheduled = scheduled.truncatedTo(ChronoUnit.HOURS);

            assertThat(captured.getStartTime().toInstant(), equalTo(truncatedScheduled.minus(Duration.ofHours(1))));
            assertThat(captured.getEndTime().map(ZonedDateTime::toInstant), equalTo(Optional.of(truncatedScheduled)));
        }
    }

    @RunWith(Parameterized.class)
    public static final class ValidResponses extends BaseTestCases {

        @Parameterized.Parameter(0)
        public String testcase;

        @Parameterized.Parameter(1)
        public List<Map<String, String>> firingTags;

        @Parameterized.Parameters(name = "{0}")
        public static Collection<Object[]> values() {
            return Arrays.asList(new Object[][]{
                    {"singleSeriesNotFiring", ImmutableList.of()},
                    {"singleSeriesFiring", ImmutableList.of(ImmutableMap.of())},
                    {"groupBySomeFiring", ImmutableList.of(
                            ImmutableMap.of("os", "mac"),
                            ImmutableMap.of("os", "windows")
                    )},
                    {"groupByNoneFiring", ImmutableList.of()}
            });
        }

        @Test
        public void testHandlesValidResponse() throws Exception {
            final MetricsQueryResult mockResult = getTestcase(testcase);
            when(_executor.periodHint(any())).thenReturn(Optional.of(ChronoUnit.MINUTES));
            when(_executor.executeQuery(any())).thenReturn(CompletableFuture.completedFuture(mockResult));
            final AlertEvaluationResult result = _context.execute(_alert, Instant.now()).toCompletableFuture().get(
                    TEST_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);

            assertThat(result.getName(), equalTo(TEST_METRIC));
            assertThat(result.getFiringTags(), is(equalTo(firingTags)));
        }
    }

    @RunWith(Parameterized.class)
    public static final class InvalidResponses extends BaseTestCases {

        @Parameterized.Parameter(0)
        public String testcase;

        @Parameterized.Parameters(name = "{0}")
        public static Collection<Object[]> values() {
            return Arrays.asList(new Object[][]{
                    {"oneResultMissingGroupBy"},
                    {"multipleResultsWithoutAGroupBy"},
                    {"moreThanOneQuery"},
                    {"mismatchedNamesInResults"},
                    {"noResults"}
            });
        }

        @Test(expected = ExecutionException.class)
        public void testThrowsExceptionOnUnexpectedResponse() throws Exception {
            final MetricsQueryResult mockResult = getTestcase(testcase);
            when(_executor.executeQuery(any())).thenReturn(CompletableFuture.completedFuture(mockResult));
            _context.execute(_alert, Instant.now()).toCompletableFuture().get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }
}
