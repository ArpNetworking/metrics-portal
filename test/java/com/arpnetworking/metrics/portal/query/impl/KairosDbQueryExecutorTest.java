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
import models.internal.MetricsQuery;
import models.internal.MetricsQueryFormat;
import models.internal.MetricsQueryResult;
import models.internal.TimeSeriesResult;
import models.internal.impl.DefaultMetricsQuery;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.fail;

public class KairosDbQueryExecutorTest {

    private KairosDbService _service;
    private KairosDbQueryExecutor _executor;
    private ObjectMapper _objectMapper;

    @Before
    public void setUp() {
        _service = Mockito.mock(KairosDbService.class);
        _objectMapper = SerializationTestUtils.getApiObjectMapper()

        _executor = new KairosDbQueryExecutor(
                _service,
                _objectMapper
        );
    }

    @Test
    public void testExecuteQuery() throws IOException {
        final String request = ResourceHelper.loadResource(getClass(), "exampleRequest");
        final String responseJSON = ResourceHelper.loadResource(getClass(), "exampleResponse");
        final MetricsQueryResponse response = _objectMapper.readValue(responseJSON, MetricsQueryResponse.class);

        final ArgumentCaptor<com.arpnetworking.kairos.client.models.MetricsQuery> captor = ArgumentCaptor.forClass(
                com.arpnetworking.kairos.client.models.MetricsQuery.class);
        Mockito.when(_service.queryMetrics(captor.capture())).thenReturn(CompletableFuture.completedFuture(response));

        final MetricsQuery query = new DefaultMetricsQuery.Builder()
                .setQuery(request)
                .setFormat(MetricsQueryFormat.KAIROS_DB)
                .build();
        final MetricsQueryResult result;
        try {
            result = _executor.executeQuery(query).toCompletableFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            fail("Unexpected exception: " + e);
            return;
        }

        assertThat(result.getErrors(), is(empty()));
        assertThat(result.getWarnings(), is(empty()));
        assertThat(result.getQueryResult().getQueries(), hasSize(1));

        final TimeSeriesResult.Query tsQuery = result.getQueryResult().getQueries().get(0);
        assertThat(tsQuery.getResults(), hasSize(4));
        assertThat(tsQuery.getResults(), hasSize(4));

        final TimeSeriesResult.Result tsResult = tsQuery.getResults().get(0);
        assertThat(tsResult.getName(), equalTo("client/samples_sink/submitted"));
        assertThat(tsResult.getValues(), not(empty()));
        assertThat(tsResult.getTags().asMap(), equalTo(ImmutableMap.of(
                "os_name", ImmutableList.of("ios"),
                "environment", ImmutableList.of("production", "staging", "test")
        )));
        assertThat(tsResult.getGroupBy(), hasSize(2));
    }

    @Test(expected = ExecutionException.class)
    public void testQueryParsingException() throws Exception {
        final MetricsQuery invalidQuery = new DefaultMetricsQuery.Builder()
                .setQuery("This isn't valid JSON")
                .setFormat(MetricsQueryFormat.KAIROS_DB)
                .build();
        _executor.executeQuery(invalidQuery).toCompletableFuture().get();
    }

    @Test(expected = ExecutionException.class)
    public void testInvalidQueryFormat() throws Exception {
        final MetricsQuery invalidQuery = new DefaultMetricsQuery.Builder()
                .setQuery("This isn't valid JSON")
                .setFormat(MetricsQueryFormat.KAIROS_DB)
                .build();
        _executor.executeQuery(invalidQuery).toCompletableFuture().get();
    }
}