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
import com.arpnetworking.testing.SerializationTestUtils;
import com.arpnetworking.utility.test.ResourceHelper;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.empty;
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
public class AlertExecutionContextTest {
    private static final String LATEST_TIMESTAMP_MS = "LATEST_TIMESTAMP_MS";
    private static final String TEST_METRIC = "test_metric";

    private static final TypeReference<Map<String, models.view.MetricsQueryResult>> MAP_TYPE_REFERENCE =
            new TypeReference<Map<String, models.view.MetricsQueryResult>>() {};

    private AlertExecutionContext _context;
    private Alert _alert;
    private Schedule _schedule;
    private QueryExecutor _executor;
    private ObjectMapper _objectMapper;

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
        when(_executor.periodHint(any())).thenReturn(Optional.of(ChronoUnit.HOURS));
        _context = new AlertExecutionContext(
                _schedule,
                _executor,
                Duration.ZERO
        );
        _objectMapper = SerializationTestUtils.getApiObjectMapper();
    }

    @Test
    public void testReturnsTheConfiguredSchedule() {
        assertThat(_context.getSchedule(_alert), equalTo(_schedule));
    }

    @Test
    public void testAppliesExpectedTimeRangeWithOffset() {
        final Duration queryOffset = Duration.ofMinutes(3);
        _context = new AlertExecutionContext(
                _schedule,
                _executor,
                queryOffset
        );
        final CompletableFuture<MetricsQueryResult> pendingResponse = new CompletableFuture<>();
        final ArgumentCaptor<BoundedMetricsQuery> captor = ArgumentCaptor.forClass(BoundedMetricsQuery.class);
        when(_executor.executeQuery(captor.capture())).thenReturn(pendingResponse);

        final List<ChronoUnit> periodTestCases = ImmutableList.of(ChronoUnit.MINUTES, ChronoUnit.HOURS);

        final Instant scheduled = Instant.now().truncatedTo(ChronoUnit.HOURS);

        for (final ChronoUnit period : periodTestCases) {
            when(_executor.periodHint(any())).thenReturn(Optional.of(period));

            _context.execute(_alert, scheduled);

            final BoundedMetricsQuery captured = captor.getValue();
            final Instant expectedStart = scheduled.minus(queryOffset).truncatedTo(period).minus(period.getDuration());
            final Instant expectedEnd = expectedStart.plus(period.getDuration());

            assertThat(captured.getStartTime().toInstant(), equalTo(expectedStart));
            assertThat(captured.getEndTime().map(this::zonedDateTimeToUTC), equalTo(Optional.of(expectedEnd)));
        }
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
        assertThat(captured.getEndTime().map(this::zonedDateTimeToUTC), equalTo(Optional.of(truncatedScheduled)));

        // Scheduled for one week ago

        scheduled = Instant.now().minus(Duration.ofDays(7));
        _context.execute(_alert, scheduled);

        captured = captor.getValue();
        truncatedScheduled = scheduled.truncatedTo(ChronoUnit.MINUTES);

        assertThat(captured.getStartTime().toInstant(), equalTo(truncatedScheduled.minus(Duration.ofMinutes(1))));
        assertThat(captured.getEndTime().map(this::zonedDateTimeToUTC), equalTo(Optional.of(truncatedScheduled)));

        // Scheduled for now, hourly

        when(_executor.periodHint(any())).thenReturn(Optional.of(ChronoUnit.HOURS));

        scheduled = Instant.now();
        _context.execute(_alert, scheduled);

        captured = captor.getValue();
        truncatedScheduled = scheduled.truncatedTo(ChronoUnit.HOURS);

        assertThat(captured.getStartTime().toInstant(), equalTo(truncatedScheduled.minus(Duration.ofHours(1))));
        assertThat(captured.getEndTime().map(this::zonedDateTimeToUTC), equalTo(Optional.of(truncatedScheduled)));
    }

    @Test
    public void testItSetsTheQueryRangeOnTheResult() throws Exception {
        final Instant scheduled = Instant.now();
        final MetricsQueryResult mockResult = getTestcase("singleSeriesNotFiring");
        final ArgumentCaptor<BoundedMetricsQuery> captor = ArgumentCaptor.forClass(BoundedMetricsQuery.class);
        when(_executor.periodHint(any())).thenReturn(Optional.of(ChronoUnit.MINUTES));
        when(_executor.executeQuery(captor.capture())).thenReturn(CompletableFuture.completedFuture(mockResult));
        final AlertEvaluationResult result =
                _context.execute(_alert, scheduled)
                        .toCompletableFuture()
                        .get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        final BoundedMetricsQuery captured = captor.getValue();
        assertThat(result.getSeriesName(), equalTo(TEST_METRIC));
        assertThat(result.getFiringTags(), is(empty()));
        assertThat(result.getGroupBys(), equalTo(ImmutableList.of()));
        assertThat(result.getQueryStartTime(), equalTo(captured.getStartTime().toInstant()));
        assertThat(result.getQueryEndTime(), equalTo(captured.getEndTime().get().toInstant()));
    }

    @Test
    public void testSingleSeriesNotFiring() throws Exception {
        final MetricsQueryResult mockResult = getTestcase("singleSeriesNotFiring");
        when(_executor.periodHint(any())).thenReturn(Optional.of(ChronoUnit.MINUTES));
        when(_executor.executeQuery(any())).thenReturn(CompletableFuture.completedFuture(mockResult));
        final AlertEvaluationResult result =
                _context.execute(_alert, Instant.now())
                        .toCompletableFuture()
                        .get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(result.getSeriesName(), equalTo(TEST_METRIC));
        assertThat(result.getFiringTags(), is(empty()));
        assertThat(result.getGroupBys(), equalTo(ImmutableList.of()));
    }

    @Test
    public void testSingleSeriesFiring() throws Exception {
        final Instant scheduled = Instant.now();
        final MetricsQueryResult mockResult = getTestcase("singleSeriesWithData");
        when(_executor.executeQuery(any())).thenReturn(CompletableFuture.completedFuture(mockResult));
        final AlertEvaluationResult result = _context.execute(_alert, scheduled)
                .toCompletableFuture()
                .get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(result.getSeriesName(), equalTo(TEST_METRIC));
        assertThat(result.getFiringTags(), equalTo(ImmutableList.of(ImmutableMap.of())));
        assertThat(result.getGroupBys(), equalTo(ImmutableList.of()));
    }

    @Test
    public void testSingleSeriesWithGroupFiring() throws Exception {
        final Instant scheduled = Instant.now();
        final MetricsQueryResult mockResult = getTestcase("singleSeriesWithGroupByWithData");
        when(_executor.executeQuery(any())).thenReturn(CompletableFuture.completedFuture(mockResult));
        final AlertEvaluationResult result = _context.execute(_alert, scheduled)
                .toCompletableFuture()
                .get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(result.getSeriesName(), equalTo(TEST_METRIC));
        assertThat(result.getFiringTags(), equalTo(ImmutableList.of(ImmutableMap.of("os", "linux"))));
        assertThat(result.getGroupBys(), equalTo(ImmutableList.of("os")));
    }

    @Test
    public void testSingleSeriesDatapointTooOld() throws Exception {
        final ChronoUnit period = ChronoUnit.HOURS;
        when(_executor.periodHint(any())).thenReturn(Optional.of(period));

        final Instant scheduled = Instant.now();
        final MetricsQueryResult mockResult = getTestcase("singleSeriesWithData", ImmutableMap.of(
                LATEST_TIMESTAMP_MS, scheduled.minus(1, period).toEpochMilli()
        ));

        when(_executor.executeQuery(any())).thenReturn(CompletableFuture.completedFuture(mockResult));
        final AlertEvaluationResult result =
            _context.execute(_alert, scheduled)
                    .toCompletableFuture()
                    .get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(result.getSeriesName(), equalTo(TEST_METRIC));
        assertThat(result.getFiringTags(), equalTo(ImmutableList.of()));
        assertThat(result.getGroupBys(), equalTo(ImmutableList.of()));
    }

    @Test
    public void testGroupBySomeFiring() throws Exception {
        final Instant scheduled = Instant.now();
        final MetricsQueryResult mockResult = getTestcase("groupBySomeFiring");
        when(_executor.executeQuery(any())).thenReturn(CompletableFuture.completedFuture(mockResult));
        final AlertEvaluationResult result =
                _context.execute(_alert, scheduled)
                        .toCompletableFuture()
                        .get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        final ImmutableList<ImmutableMap<String, String>> expectedFiringTags = ImmutableList.of(
                ImmutableMap.of("os", "mac"),
                ImmutableMap.of("os", "windows")
        );

        assertThat(result.getSeriesName(), equalTo(TEST_METRIC));
        assertThat(result.getFiringTags(), equalTo(expectedFiringTags));
        assertThat(result.getGroupBys(), equalTo(ImmutableList.of("os")));
    }

    @Test
    public void testGroupByNoneFiring() throws Exception {
        final MetricsQueryResult mockResult = getTestcase("groupByNoneFiring");
        when(_executor.executeQuery(any())).thenReturn(CompletableFuture.completedFuture(mockResult));
        final AlertEvaluationResult result =
                _context.execute(_alert, Instant.now())
                        .toCompletableFuture()
                        .get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(result.getSeriesName(), equalTo(TEST_METRIC));
        assertThat(result.getFiringTags(), is(empty()));
        assertThat(result.getGroupBys(), equalTo(ImmutableList.of("os")));
    }

    @Test
    public void testEmptyResult() throws Exception {
        final MetricsQueryResult mockResult = getTestcase("emptyResult");
        when(_executor.executeQuery(any())).thenReturn(CompletableFuture.completedFuture(mockResult));
        final AlertEvaluationResult result =
                _context.execute(_alert, Instant.now())
                        .toCompletableFuture()
                        .get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(result.getSeriesName(), equalTo(TEST_METRIC));
        assertThat(result.getFiringTags(), is(empty()));
        assertThat(result.getGroupBys(), is(empty()));
    }

    @Test(expected = ExecutionException.class)
    public void testQueryExecuteError() throws Exception {
        final Throwable queryError = new RuntimeException("Something went wrong");
        final CompletableFuture<MetricsQueryResult> exceptionalCompletionStage = new CompletableFuture<>();
        exceptionalCompletionStage.completeExceptionally(queryError);
        when(_executor.executeQuery(any())).thenReturn(exceptionalCompletionStage);

        _context.execute(_alert, Instant.now())
                .toCompletableFuture()
                .get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @Test(expected = ExecutionException.class)
    public void testOneResultMissingGroupBy() throws Exception {
        final MetricsQueryResult mockResult = getTestcase("oneResultMissingGroupBy");
        when(_executor.executeQuery(any())).thenReturn(CompletableFuture.completedFuture(mockResult));
        _context.execute(_alert, Instant.now()).toCompletableFuture().get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @Test(expected = ExecutionException.class)
    public void testMultipleResultsWithoutAGroupBy() throws Exception {
        final MetricsQueryResult mockResult = getTestcase("multipleResultsWithoutAGroupBy");
        when(_executor.executeQuery(any())).thenReturn(CompletableFuture.completedFuture(mockResult));
        _context.execute(_alert, Instant.now()).toCompletableFuture().get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @Test(expected = ExecutionException.class)
    public void testMoreThanOneQuery() throws Exception {
        final MetricsQueryResult mockResult = getTestcase("moreThanOneQuery");
        when(_executor.executeQuery(any())).thenReturn(CompletableFuture.completedFuture(mockResult));
        _context.execute(_alert, Instant.now()).toCompletableFuture().get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @Test(expected = ExecutionException.class)
    public void testMismatchedNamesInResults() throws Exception {
        final MetricsQueryResult mockResult = getTestcase("mismatchedNamesInResults");
        when(_executor.executeQuery(any())).thenReturn(CompletableFuture.completedFuture(mockResult));
        _context.execute(_alert, Instant.now()).toCompletableFuture().get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @Test(expected = ExecutionException.class)
    public void testNoResults() throws Exception {
        final MetricsQueryResult mockResult = getTestcase("noResults");
        when(_executor.executeQuery(any())).thenReturn(CompletableFuture.completedFuture(mockResult));
        _context.execute(_alert, Instant.now()).toCompletableFuture().get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @Test(expected = ExecutionException.class)
    public void testMissingPeriodHint() throws Exception {
        when(_executor.periodHint(any())).thenReturn(Optional.empty());
        when(_executor.executeQuery(any())).thenReturn(new CompletableFuture<>());
        _context.execute(_alert, Instant.now()).toCompletableFuture().get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private MetricsQueryResult getTestcase(final String name) throws IOException {
        final long latestDatapointMs = Instant.now()
                .truncatedTo(ChronoUnit.MINUTES)
                .toEpochMilli();

        return getTestcase(name, ImmutableMap.of(
                LATEST_TIMESTAMP_MS, latestDatapointMs
        ));
    }

    private MetricsQueryResult getTestcase(final String name, final Map<String, Object> bindings) throws IOException {
        String json = ResourceHelper.loadResource(getClass(), "resultTestCases");
        for (final Map.Entry<String, Object> entry : bindings.entrySet()) {
            json = json.replace(
                    "\"" + entry.getKey() + "\"",
                    _objectMapper.writeValueAsString(entry.getValue())
            );
        }

        final Map<String, models.view.MetricsQueryResult> testcases =
                _objectMapper.reader()
                        .withFeatures(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                        .forType(MAP_TYPE_REFERENCE)
                        .readValue(json);


        final models.view.MetricsQueryResult result = testcases.get(name);
        if (result == null) {
            fail("Could not find testcase: " + name);
        }
        return result.toInternal();
    }

    private Instant zonedDateTimeToUTC(final ZonedDateTime dateTime) {
        // We always work with UTC instants but since queries can have other
        // zones we should not assume UTC in these tests.
        return dateTime.withZoneSameInstant(ZoneOffset.UTC).toInstant();
    }
}
