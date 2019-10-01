/*
 * Copyright 2019 Dropbox Inc.
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
package com.arpnetworking.kairos.service;

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.KairosMetricNamesQueryResponse;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.TagNamesResponse;
import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.Timer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.ebeaninternal.util.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public class KairosDbServiceImplTest {
    private static final String CLASS_NAME = KairosDbServiceImplTest.class.getSimpleName();

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();

    @Mock
    private KairosDbClient _mockClient;
    @Mock
    private MetricsFactory _mockMetricsFactory;
    @Mock
    private Metrics _mockMetrics;

    private KairosDbServiceImpl _service;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        _service = new KairosDbServiceImpl.Builder()
                .setKairosDbClient(_mockClient)
                .setMetricsFactory(_mockMetricsFactory)
                .setExcludedTagNames(ImmutableSet.of("host"))
                .build();
        when(_mockClient.queryMetricNames())
                .thenReturn(CompletableFuture.completedFuture(new KairosMetricNamesQueryResponse.Builder()
                        .setResults(ImmutableList.<String>builder().add("foo", "foo_1h", "foo_1d", "bar").build())
                        .build())
                );

        when(_mockMetricsFactory.create()).thenReturn(_mockMetrics);
        when(_mockMetrics.createTimer(any())).thenReturn(Mockito.mock(Timer.class));
    }

    @Test
    public void testFiltersRollupOverrideForTagsQueries() throws Exception {
        when(_mockClient.queryMetricTags(any())).thenReturn(
                CompletableFuture.completedFuture(
                        OBJECT_MAPPER.readValue(
                                readResource("testFiltersRollupOverrideForTagsQueries.backend_response"),
                                MetricsQueryResponse.class)
                )
        );

        _service.queryMetricTags(
                OBJECT_MAPPER.readValue(
                        readResource("testFiltersRollupOverrideForTagsQueries.request"),
                        MetricsQuery.class)
        );

        final ArgumentCaptor<MetricsQuery> captor = ArgumentCaptor.forClass(MetricsQuery.class);
        verify(_mockClient, times(1)).queryMetricTags(captor.capture());
        final MetricsQuery request = captor.getValue();
        assertEquals(Instant.ofEpochMilli(0), request.getStartTime());
        assertEquals(1, request.getMetrics().size());
        final Metric metric = request.getMetrics().get(0);
        assertEquals("foo", metric.getName());
    }


    @Test
    public void testFiltersRollupMetrics() throws Exception {
        final CompletionStage<KairosMetricNamesQueryResponse> future = _service.queryMetricNames(Optional.empty(), true);
        final KairosMetricNamesQueryResponse response = future.toCompletableFuture().get();
        assertEquals(ImmutableList.builder().add("foo", "bar").build(), response.getResults());
    }

    @Test
    public void testSelectsRollupMetricsBasedOnAggregate() throws Exception {
        when(_mockClient.queryMetrics(any())).thenReturn(
                CompletableFuture.completedFuture(
                        OBJECT_MAPPER.readValue(
                                readResource("testSelectsRollupMetricsBasedOnAggregate.backend_response"),
                                MetricsQueryResponse.class)
                )
        );


        _service.queryMetrics(
                OBJECT_MAPPER.readValue(
                        readResource("testSelectsRollupMetricsBasedOnAggregate.request"),
                        MetricsQuery.class)
        );

        final ArgumentCaptor<MetricsQuery> captor = ArgumentCaptor.forClass(MetricsQuery.class);
        verify(_mockClient, times(1)).queryMetrics(captor.capture());
        final MetricsQuery request = captor.getValue();
        assertEquals(Instant.ofEpochMilli(1), request.getStartTime());
        assertEquals(1, request.getMetrics().size());
        final Metric metric = request.getMetrics().get(0);
        assertEquals("foo_1h", metric.getName());
    }

    @Test
    public void testIgnoresRollupsForUnalignedAggregate() throws Exception {
        when(_mockClient.queryMetrics(any())).thenReturn(
                CompletableFuture.completedFuture(
                        OBJECT_MAPPER.readValue(
                                readResource("testIgnoresRollupsForUnalignedAggregate.backend_response"),
                                MetricsQueryResponse.class
                        )
                )
        );

        _service.queryMetrics(
                OBJECT_MAPPER.readValue(
                        readResource("testIgnoresRollupsForUnalignedAggregate.request"),
                        MetricsQuery.class)
        );

        final ArgumentCaptor<MetricsQuery> captor = ArgumentCaptor.forClass(MetricsQuery.class);
        verify(_mockClient, times(1)).queryMetrics(captor.capture());
        final MetricsQuery request = captor.getValue();
        assertEquals(Instant.ofEpochMilli(1), request.getStartTime());
        assertEquals(1, request.getMetrics().size());
        final Metric metric = request.getMetrics().get(0);
        assertEquals("foo", metric.getName());
    }

    @Test
    public void testSelectsSmallestRollupBasedOnAggregate() throws Exception {
        when(_mockClient.queryMetrics(any())).thenReturn(
                CompletableFuture.completedFuture(
                        OBJECT_MAPPER.readValue(
                                readResource("testSelectsSmallestRollupBasedOnAggregate.backend_response"),
                                MetricsQueryResponse.class
                        )
                )
        );

        _service.queryMetrics(
                OBJECT_MAPPER.readValue(
                        readResource("testSelectsSmallestRollupBasedOnAggregate.request"),
                        MetricsQuery.class)
        );

        final ArgumentCaptor<MetricsQuery> captor = ArgumentCaptor.forClass(MetricsQuery.class);
        verify(_mockClient, times(1)).queryMetrics(captor.capture());
        final MetricsQuery request = captor.getValue();
        assertEquals(Instant.ofEpochMilli(1), request.getStartTime());
        assertEquals(1, request.getMetrics().size());
        final Metric metric = request.getMetrics().get(0);
        assertEquals("foo_1h", metric.getName());
    }

    @Test
    public void testIgnoresRollupsForSpecialCase() throws Exception {
        when(_mockClient.queryMetrics(any())).thenReturn(
                CompletableFuture.completedFuture(
                        OBJECT_MAPPER.readValue(
                                readResource("testIgnoresRollupsForSpecialCase.backend_response"),
                                MetricsQueryResponse.class
                        )
                )
        );

        _service.queryMetrics(
                OBJECT_MAPPER.readValue(
                        readResource("testIgnoresRollupsForSpecialCase.request"),
                        MetricsQuery.class)
        );

        final ArgumentCaptor<MetricsQuery> captor = ArgumentCaptor.forClass(MetricsQuery.class);
        verify(_mockClient, times(1)).queryMetrics(captor.capture());
        final MetricsQuery request = captor.getValue();
        assertEquals(Instant.ofEpochMilli(1), request.getStartTime());
        assertEquals(1, request.getMetrics().size());
        final Metric metric = request.getMetrics().get(0);
        assertEquals("foo", metric.getName());
    }

    @Test
    public void testFilterTagNamesOnListTagNames() throws Exception {
        when(_mockClient.listTagNames()).thenReturn(
                CompletableFuture.completedFuture(
                        OBJECT_MAPPER.readValue(
                                readResource("testFilterTagNamesOnListTagNames.backend_response"),
                                TagNamesResponse.class
                        )
                )
        );

        final TagNamesResponse response = _service.listTagNames().toCompletableFuture().get();

        verify(_mockClient).listTagNames();
        assertEquals(
                ImmutableSet.of(
                        "os_name",
                        "os_version",
                        "app_version",
                        "country"),
                response.getResults());
    }

    private String readResource(final String resourceSuffix) {
        try {
            return IOUtils.readUtf8(getClass()
                    .getClassLoader()
                    .getResourceAsStream("com/arpnetworking/kairos/service/" + CLASS_NAME + "." + resourceSuffix + ".json"));
        } catch (final IOException e) {
            fail("Failed with exception: " + e);
            return null;
        }
    }
}
