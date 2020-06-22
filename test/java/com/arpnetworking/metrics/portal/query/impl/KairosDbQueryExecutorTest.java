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

package com.arpnetworking.metrics.portal.query.impl;

import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.service.KairosDbService;
import com.arpnetworking.testing.SerializationTestUtils;
import com.arpnetworking.utility.test.ResourceHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import models.internal.BoundedMetricsQuery;
import models.internal.MetricsQuery;
import models.internal.MetricsQueryFormat;
import models.internal.MetricsQueryResult;
import models.internal.TimeSeriesResult;
import models.internal.impl.DefaultBoundedMetricsQuery;
import models.internal.impl.DefaultMetricsQuery;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KairosDbQueryExecutor}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@RunWith(Enclosed.class)
public class KairosDbQueryExecutorTest {
    public abstract static class BaseTests {
        protected KairosDbService _service;
        protected KairosDbQueryExecutor _executor;
        protected ObjectMapper _objectMapper;

        @Before
        public void setUp() {
            _service = Mockito.mock(KairosDbService.class);
            _objectMapper = SerializationTestUtils.getApiObjectMapper();
            _executor = new KairosDbQueryExecutor(
                    _service,
                    _objectMapper
            );
        }
    }

    public static final class ExecuteQueryTests extends BaseTests {
        private static final ZonedDateTime QUERY_START_TIME = ZonedDateTime.parse(
                "2020-06-16T00:00-07:00[America/Los_Angeles]");
        private static final ZonedDateTime QUERY_END_TIME = ZonedDateTime.parse(
                "2020-06-16T00:01-07:00[America/Los_Angeles]");

        @Test
        public void testExecuteQuery() throws IOException {
            final String responseJSON = ResourceHelper.loadResource(
                    KairosDbQueryExecutorTest.class,
                    "testResponse");
            final MetricsQueryResponse response = _objectMapper.readValue(responseJSON, MetricsQueryResponse.class);

            final ArgumentCaptor<com.arpnetworking.kairos.client.models.MetricsQuery> captor = ArgumentCaptor.forClass(
                    com.arpnetworking.kairos.client.models.MetricsQuery.class);
            when(_service.queryMetrics(captor.capture())).thenReturn(CompletableFuture.completedFuture(response));

            final BoundedMetricsQuery query = loadTestQuery();
            final com.arpnetworking.kairos.client.models.MetricsQuery parsedQuery =
                    _objectMapper.readValue(query.getQuery(), com.arpnetworking.kairos.client.models.MetricsQuery.class);

            final MetricsQueryResult result;
            try {
                result = _executor.executeQuery(query).toCompletableFuture().get();
            } catch (final InterruptedException | ExecutionException e) {
                fail("Unexpected exception: " + e);
                return;
            }

            final com.arpnetworking.kairos.client.models.MetricsQuery capturedQuery = captor.getValue();
            assertThat(capturedQuery.getMetrics(), equalTo(parsedQuery.getMetrics()));
            assertThat(capturedQuery.getOtherArgs(), equalTo(parsedQuery.getOtherArgs()));
            assertThat(capturedQuery.getStartTime(), equalTo(Optional.of(QUERY_START_TIME.toInstant())));
            assertThat(capturedQuery.getEndTime(), equalTo(Optional.of(QUERY_END_TIME.toInstant())));

            assertThat(result.getErrors(), is(empty()));
            assertThat(result.getWarnings(), is(empty()));
            assertThat(result.getQueryResult().getQueries(), hasSize(1));

            final TimeSeriesResult.Query tsQuery = result.getQueryResult().getQueries().get(0);
            assertThat(tsQuery.getResults(), hasSize(4));
            assertThat(tsQuery.getResults(), hasSize(4));

            final TimeSeriesResult.Result tsResult = tsQuery.getResults().get(0);
            assertThat(tsResult.getName(), equalTo("client/samples_sink/submitted_samples"));
            assertThat(tsResult.getValues(), not(empty()));
            assertThat(tsResult.getTags().asMap(), equalTo(ImmutableMap.of(
                    "os", ImmutableList.of("linux"),
                    "environment", ImmutableList.of("production", "staging", "test")
            )));
            assertThat(tsResult.getGroupBy(), hasSize(2));
        }

        @Test(expected = ExecutionException.class)
        public void testKairosDBReturnsError() throws Exception {
            when(_service.queryMetrics(any())).thenThrow(new RuntimeException("boom"));
            final BoundedMetricsQuery query = loadTestQuery();
            _executor.executeQuery(query).toCompletableFuture().get();
        }

        @Test(expected = ExecutionException.class)
        public void testQueryParsingException() throws Exception {
            final BoundedMetricsQuery invalidQuery = new DefaultBoundedMetricsQuery.Builder()
                    .setQuery("This isn't valid JSON")
                    .setFormat(MetricsQueryFormat.KAIROS_DB)
                    .setStartTime(QUERY_START_TIME)
                    .setEndTime(QUERY_END_TIME)
                    .build();
            _executor.executeQuery(invalidQuery).toCompletableFuture().get();
        }

        private BoundedMetricsQuery loadTestQuery() throws IOException {
            final String serializedQuery = ResourceHelper.loadResource(
                    KairosDbQueryExecutorTest.class,
                    "testQuery");

            return new DefaultBoundedMetricsQuery.Builder()
                    .setQuery(serializedQuery)
                    .setFormat(MetricsQueryFormat.KAIROS_DB)
                    .setStartTime(QUERY_START_TIME)
                    .setEndTime(QUERY_END_TIME)
                    .build();
        }
    }

    @RunWith(Parameterized.class)
    public static final class PeriodHintTests extends BaseTests {
        @Parameterized.Parameter(0)
        public String testName;

        @Parameterized.Parameter(1)
        public Optional<ChronoUnit> expectedResult;

        @Parameterized.Parameters(name = "{0}")
        public static Collection<Object[]> values() {
            return Arrays.asList(new Object[][]{
                    {"periodHintMinutely", Optional.of(ChronoUnit.MINUTES)},
                    {"periodHintHourly", Optional.of(ChronoUnit.HOURS)},
                    {"periodHintNone", Optional.empty()},
                    {"periodHintMultipleMetrics", Optional.of(ChronoUnit.MINUTES)}
            });
        }

        @Test
        public void testPeriodHint() throws Exception {
            final String jsonQuery = ResourceHelper.loadResource(
                    KairosDbQueryExecutorTest.class,
                    testName
            );
            final MetricsQuery query = new DefaultMetricsQuery.Builder()
                    .setQuery(jsonQuery)
                    .setFormat(MetricsQueryFormat.KAIROS_DB)
                    .build();
            assertThat(_executor.periodHint(query), equalTo(expectedResult));
        }
    }
}
