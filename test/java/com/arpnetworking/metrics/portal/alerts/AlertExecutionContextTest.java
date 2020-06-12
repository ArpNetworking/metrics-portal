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
import com.arpnetworking.metrics.portal.query.QueryExecutionException;
import com.arpnetworking.metrics.portal.query.QueryExecutor;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.NeverSchedule;
import com.arpnetworking.utility.test.ResourceHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
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
import org.mockito.Mockito;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AlertExecutionContext}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class AlertExecutionContextTest {
    private static final String TEST_METRIC = "test_metric";
    private AlertExecutionContext _context;
    private Alert _alert;
    private Schedule _schedule;
    private QueryExecutor _executor;

    // This is small because the futures here should never actually block.
    private static final long TEST_TIMEOUT_MS = 50;

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
        _context = new AlertExecutionContext(
                _schedule,
                _executor
        );
    }

    @Test
    public void testReturnsTheConfiguredSchedule() {
        assertThat(_context.getSchedule(_alert), equalTo(_schedule));
    }

    @Test
    public void testQueryExecuteError() throws QueryExecutionException, InterruptedException {
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
    public void testSingleSeriesNotFiring() throws Exception {
        final MetricsQueryResult mockResult = new DefaultMetricsQueryResult.Builder()
                .setQueryResult(new DefaultTimeSeriesResult.Builder()
                    .setQueries(ImmutableList.of(
                        new DefaultTimeSeriesResult.Query.Builder()
                            .setSampleSize(1000L)
                            .setResults(ImmutableList.of(
                                new DefaultTimeSeriesResult.Result.Builder()
                                    .setName(TEST_METRIC)
                                    .setTags(ImmutableMultimap.of(
                                            "os", "linux",
                                            "os", "mac",
                                            "os", "windows"
                                    ))
                                    .build()
                            ))
                            .build()
                    ))
                    .build()
                )
                .build();

        when(_executor.executeQuery(eq(_alert.getQuery()))).thenReturn(CompletableFuture.completedFuture(mockResult));
        final AlertEvaluationResult result = _context.execute(_alert, Instant.now()).toCompletableFuture().get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(result.getName(), equalTo(TEST_METRIC));
        assertThat(result.getFiringTags(), is(empty()));
    }

    @Test
    public void testSingleSeriesFiring() throws Exception {
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
                                                                    .setTime(Instant.now())
                                                                    .setValue(1)
                                                                    .build()
                                                        ))
                                                        .setTags(ImmutableMultimap.of(
                                                                "os", "linux",
                                                                "os", "mac",
                                                                "os", "windows"
                                                        ))
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build()
                )
                .build();

        when(_executor.executeQuery(eq(_alert.getQuery()))).thenReturn(CompletableFuture.completedFuture(mockResult));
        final AlertEvaluationResult result = _context.execute(_alert, Instant.now()).toCompletableFuture().get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(result.getName(), equalTo(TEST_METRIC));
        assertThat(result.getFiringTags(), equalTo(ImmutableList.of(ImmutableMap.of())));
    }

    @Test
    public void testGroupBySomeFiring() throws Exception {
        final MetricsQueryResult mockResult = new DefaultMetricsQueryResult.Builder()
                .setQueryResult(new DefaultTimeSeriesResult.Builder()
                        .setQueries(ImmutableList.of(
                                new DefaultTimeSeriesResult.Query.Builder()
                                        .setSampleSize(1000L)
                                        .setResults(ImmutableList.of(
                                                new DefaultTimeSeriesResult.Result.Builder()
                                                        .setName(TEST_METRIC)
                                                        .setTags(ImmutableMultimap.of(
                                                                "os", "linux"
                                                        ))
                                                        .setGroupBy(ImmutableList.of(
                                                                new DefaultTimeSeriesResult.QueryTagGroupBy.Builder()
                                                                        .setGroup(ImmutableMap.of("os", "linux"))
                                                                        .setTags(ImmutableList.of("os"))
                                                                        .build()
                                                        ))
                                                        .build(),
                                                new DefaultTimeSeriesResult.Result.Builder()
                                                        .setName(TEST_METRIC)
                                                        .setTags(ImmutableMultimap.of(
                                                                "os", "mac"
                                                        ))
                                                        .setGroupBy(ImmutableList.of(
                                                                new DefaultTimeSeriesResult.QueryTagGroupBy.Builder()
                                                                        .setGroup(ImmutableMap.of("os", "mac"))
                                                                        .setTags(ImmutableList.of("os"))
                                                                        .build()
                                                        ))
                                                        .setValues(ImmutableList.of(
                                                                new DefaultTimeSeriesResult.DataPoint.Builder()
                                                                        .setTime(Instant.now())
                                                                        .setValue(1)
                                                                        .build()
                                                        ))
                                                        .build(),
                                                new DefaultTimeSeriesResult.Result.Builder()
                                                        .setName(TEST_METRIC)
                                                        .setTags(ImmutableMultimap.of(
                                                                "os", "windows"
                                                        ))
                                                        .setGroupBy(ImmutableList.of(
                                                                new DefaultTimeSeriesResult.QueryTagGroupBy.Builder()
                                                                        .setGroup(ImmutableMap.of("os", "windows"))
                                                                        .setTags(ImmutableList.of("os"))
                                                                        .build()
                                                        ))
                                                        .setValues(ImmutableList.of(
                                                                new DefaultTimeSeriesResult.DataPoint.Builder()
                                                                        .setTime(Instant.now())
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
        when(_executor.executeQuery(eq(_alert.getQuery()))).thenReturn(CompletableFuture.completedFuture(mockResult));
        final AlertEvaluationResult result = _context.execute(_alert, Instant.now()).toCompletableFuture().get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        final ImmutableList<ImmutableMap<String, String>> expectedFiringTags = ImmutableList.of(
                ImmutableMap.of("os", "mac"),
                ImmutableMap.of("os", "windows")
        );

        assertThat(result.getName(), equalTo(TEST_METRIC));
        assertThat(result.getFiringTags(), equalTo(expectedFiringTags));
    }

    @Test
    public void testGroupByNoneFiring() throws Exception {
        final MetricsQueryResult mockResult = new DefaultMetricsQueryResult.Builder()
                .setQueryResult(new DefaultTimeSeriesResult.Builder()
                        .setQueries(ImmutableList.of(
                                new DefaultTimeSeriesResult.Query.Builder()
                                        .setSampleSize(1000L)
                                        .setResults(ImmutableList.of(
                                                new DefaultTimeSeriesResult.Result.Builder()
                                                        .setName(TEST_METRIC)
                                                        .setTags(ImmutableMultimap.of(
                                                            "os", "linux"
                                                        ))
                                                        .setGroupBy(ImmutableList.of(
                                                                new DefaultTimeSeriesResult.QueryTagGroupBy.Builder()
                                                                    .setGroup(ImmutableMap.of("os", "linux"))
                                                                    .setTags(ImmutableList.of("os"))
                                                                    .build()
                                                        ))
                                                        .build(),
                                                new DefaultTimeSeriesResult.Result.Builder()
                                                        .setName(TEST_METRIC)
                                                        .setTags(ImmutableMultimap.of(
                                                              "os", "mac"
                                                        ))
                                                        .setGroupBy(ImmutableList.of(
                                                                new DefaultTimeSeriesResult.QueryTagGroupBy.Builder()
                                                                        .setGroup(ImmutableMap.of("os", "mac"))
                                                                        .setTags(ImmutableList.of("os"))
                                                                        .build()
                                                        ))
                                                        .build(),
                                                new DefaultTimeSeriesResult.Result.Builder()
                                                        .setName(TEST_METRIC)
                                                        .setTags(ImmutableMultimap.of(
                                                             "os", "windows"
                                                        ))
                                                        .setGroupBy(ImmutableList.of(
                                                                new DefaultTimeSeriesResult.QueryTagGroupBy.Builder()
                                                                        .setGroup(ImmutableMap.of("os", "windows"))
                                                                        .setTags(ImmutableList.of("os"))
                                                                        .build()
                                                        ))
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build()
                )
                .build();
        when(_executor.executeQuery(eq(_alert.getQuery()))).thenReturn(CompletableFuture.completedFuture(mockResult));
        final AlertEvaluationResult result = _context.execute(_alert, Instant.now()).toCompletableFuture().get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(result.getName(), equalTo(TEST_METRIC));
        assertThat(result.getFiringTags(), is(empty()));
    }

    @Test(expected = ExecutionException.class)
    public void testOneResultMissingGroupBy() throws Exception {
        final MetricsQueryResult mockResult = new DefaultMetricsQueryResult.Builder()
                .setQueryResult(new DefaultTimeSeriesResult.Builder()
                        .setQueries(ImmutableList.of(
                                new DefaultTimeSeriesResult.Query.Builder()
                                        .setSampleSize(1000L)
                                        .setResults(ImmutableList.of(
                                                new DefaultTimeSeriesResult.Result.Builder()
                                                        .setName(TEST_METRIC)
                                                        .setTags(ImmutableMultimap.of(
                                                                "os", "linux"
                                                        ))
                                                        .setGroupBy(ImmutableList.of(
                                                                new DefaultTimeSeriesResult.QueryTagGroupBy.Builder()
                                                                        .setGroup(ImmutableMap.of("os", "linux"))
                                                                        .setTags(ImmutableList.of("os"))
                                                                        .build()
                                                        ))
                                                        .build(),
                                                new DefaultTimeSeriesResult.Result.Builder()
                                                        .setName(TEST_METRIC)
                                                        .setTags(ImmutableMultimap.of(
                                                                "os", "windows"
                                                        ))
                                                        .setValues(ImmutableList.of(
                                                                new DefaultTimeSeriesResult.DataPoint.Builder()
                                                                        .setTime(Instant.now())
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
        when(_executor.executeQuery(eq(_alert.getQuery()))).thenReturn(CompletableFuture.completedFuture(mockResult));
        _context.execute(_alert, Instant.now()).toCompletableFuture().get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @Test(expected = ExecutionException.class)
    public void testMultipleResultsWithoutAGroupBy() throws Exception {
        final MetricsQueryResult mockResult = new DefaultMetricsQueryResult.Builder()
                .setQueryResult(new DefaultTimeSeriesResult.Builder()
                        .setQueries(ImmutableList.of(
                                new DefaultTimeSeriesResult.Query.Builder()
                                        .setSampleSize(1000L)
                                        .setResults(ImmutableList.of(
                                                new DefaultTimeSeriesResult.Result.Builder()
                                                        .setName(TEST_METRIC)
                                                        .setTags(ImmutableMultimap.of(
                                                                "os", "linux"
                                                        ))
                                                        .build(),
                                                new DefaultTimeSeriesResult.Result.Builder()
                                                        .setName(TEST_METRIC)
                                                        .setTags(ImmutableMultimap.of(
                                                                "os", "windows"
                                                        ))
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build()
                )
                .build();
        when(_executor.executeQuery(eq(_alert.getQuery()))).thenReturn(CompletableFuture.completedFuture(mockResult));
        _context.execute(_alert, Instant.now()).toCompletableFuture().get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @Test(expected = ExecutionException.class)
    public void testMoreThanOneQuery() throws Exception {
        final MetricsQueryResult mockResult = new DefaultMetricsQueryResult.Builder()
                .setQueryResult(new DefaultTimeSeriesResult.Builder()
                        .setQueries(ImmutableList.of(
                                new DefaultTimeSeriesResult.Query.Builder()
                                        .setSampleSize(1000L)
                                        .setResults(ImmutableList.of(
                                                new DefaultTimeSeriesResult.Result.Builder()
                                                        .setName(TEST_METRIC)
                                                        .setTags(ImmutableMultimap.of(
                                                                "foo", "bar"
                                                        ))
                                                        .build()
                                        ))
                                        .build(),
                                new DefaultTimeSeriesResult.Query.Builder()
                                        .setSampleSize(1000L)
                                        .setResults(ImmutableList.of(
                                                new DefaultTimeSeriesResult.Result.Builder()
                                                        .setName(TEST_METRIC)
                                                        .setTags(ImmutableMultimap.of(
                                                                     "foo", "bar"
                                                        ))
                                                        .build()
                                                ))
                                        .build()
                        ))
                        .build()
                )
                .build();
        when(_executor.executeQuery(eq(_alert.getQuery()))).thenReturn(CompletableFuture.completedFuture(mockResult));
        _context.execute(_alert, Instant.now()).toCompletableFuture().get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

    }

    @Test(expected = ExecutionException.class)
    public void testMismatchedNamesInResults() throws Exception {
        final MetricsQueryResult mockResult = new DefaultMetricsQueryResult.Builder()
                .setQueryResult(new DefaultTimeSeriesResult.Builder()
                        .setQueries(ImmutableList.of(
                                new DefaultTimeSeriesResult.Query.Builder()
                                        .setSampleSize(1000L)
                                        .setResults(ImmutableList.of(
                                                new DefaultTimeSeriesResult.Result.Builder()
                                                        .setName(TEST_METRIC)
                                                        .setTags(ImmutableMultimap.of(
                                                                "foo", "bar"
                                                        ))
                                                        .build(),
                                                new DefaultTimeSeriesResult.Result.Builder()
                                                        .setName("other_metric")
                                                        .setTags(ImmutableMultimap.of(
                                                                         "foo", "bar"
                                                        ))
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build()
                )
                .build();
        when(_executor.executeQuery(eq(_alert.getQuery()))).thenReturn(CompletableFuture.completedFuture(mockResult));
        _context.execute(_alert, Instant.now()).toCompletableFuture().get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @Test(expected = ExecutionException.class)
    public void testNoResults() throws Exception {
        final MetricsQueryResult mockResult = new DefaultMetricsQueryResult.Builder()
                .setQueryResult(new DefaultTimeSeriesResult.Builder()
                        .setQueries(ImmutableList.of(
                                new DefaultTimeSeriesResult.Query.Builder()
                                        .setSampleSize(1000L)
                                        .setResults(ImmutableList.of())
                                        .build()
                        ))
                        .build()
                )
                .build();
        when(_executor.executeQuery(eq(_alert.getQuery()))).thenReturn(CompletableFuture.completedFuture(mockResult));
        _context.execute(_alert, Instant.now()).toCompletableFuture().get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private CompletionStage<MetricsQueryResult> mockedResult(final String resourceName) throws Exception {
        return CompletableFuture.completedFuture(
            ResourceHelper.loadResourceAs(getClass(), resourceName, MetricsQueryResult.class)
        );
    }
}
