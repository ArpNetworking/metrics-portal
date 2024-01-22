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

import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.Aggregator;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.MetricNamesResponse;
import com.arpnetworking.kairos.client.models.MetricTags;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.kairos.client.models.RelativeDateTime;
import com.arpnetworking.kairos.client.models.Sampling;
import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.arpnetworking.kairos.client.models.TagNamesResponse;
import com.arpnetworking.kairos.client.models.TagsQuery;
import com.arpnetworking.kairos.client.models.TimeUnit;
import com.arpnetworking.kairos.config.MetricsQueryConfig;
import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.Timer;
import com.arpnetworking.metrics.impl.NoOpMetrics;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.rollups.RollupMetric;
import com.arpnetworking.rollups.RollupPeriod;
import com.arpnetworking.testing.SerializationTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public class KairosDbServiceImplTest {
    private static final String CLASS_NAME = KairosDbServiceImplTest.class.getSimpleName();

    private static final ObjectMapper OBJECT_MAPPER = SerializationTestUtils.getApiObjectMapper();

    private static final QueryContext TEST_CONTEXT = new DefaultQueryContext.Builder()
            .setOrigin(QueryOrigin.EXTERNAL_REQUEST)
            .build();

    @Mock
    private KairosDbClient _mockClient;
    @Mock
    private MetricsFactory _mockMetricsFactory;
    @Mock
    private Metrics _mockMetrics;
    @Mock
    private MetricsQueryConfig _mockQueryConfig;
    @Mock
    private Consumer<MetricsQuery> _mockRewrittenMetricsQueryConsumer;

    private KairosDbServiceImpl _service;
    private AutoCloseable _mocks;


    @Before
    public void setUp() {
        _mocks = MockitoAnnotations.openMocks(this);
        _service = new KairosDbServiceImpl.Builder()
                .setKairosDbClient(_mockClient)
                .setMetricsFactory(_mockMetricsFactory)
                .setExcludedTagNames(ImmutableSet.of("host"))
                .setMetricsQueryConfig(_mockQueryConfig)
                .setRewrittenQueryConsumer(_mockRewrittenMetricsQueryConsumer)
                .setRollupEnabledOrigins(EnumSet.of(QueryOrigin.EXTERNAL_REQUEST))
                .build();
        when(_mockClient.queryMetricNames())
                .thenReturn(CompletableFuture.completedFuture(new MetricNamesResponse.Builder()
                        .setResults(ImmutableList.<String>builder().add("foo", "foo_1h", "foo_1d", "bar").build())
                        .build())
                );

        when(_mockMetricsFactory.create()).thenReturn(_mockMetrics);
        when(_mockMetrics.createTimer(any())).thenReturn(Mockito.mock(Timer.class));

        when(_mockQueryConfig.getQueryEnabledRollups(any())).thenReturn(ImmutableSet.copyOf(SamplingUnit.values()));
    }

    @After
    public void tearDown() {
        if (_mocks != null) {
            try {
                _mocks.close();
                // CHECKSTYLE.OFF: IllegalCatch - Ignore all errors when closing the mock
            } catch (final Exception ignored) { }
                // CHECKSTYLE.ON: IllegalCatch
        }
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
                        TagsQuery.class)
        );

        final ArgumentCaptor<TagsQuery> captor = ArgumentCaptor.forClass(TagsQuery.class);
        verify(_mockClient, times(1)).queryMetricTags(captor.capture());
        final TagsQuery request = captor.getValue();
        assertEquals(Optional.of(Instant.ofEpochMilli(0)), request.getStartTime());
        assertEquals(1, request.getMetrics().size());
        final MetricTags metric = request.getMetrics().get(0);
        assertEquals("foo", metric.getName());
    }


    @Test
    public void testFiltersRollupMetrics() throws Exception {
        final CompletionStage<MetricNamesResponse> future = _service.queryMetricNames(Optional.empty(), Optional.empty(), true);
        final MetricNamesResponse response = future.toCompletableFuture().get();
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
                TEST_CONTEXT,
                OBJECT_MAPPER.readValue(
                        readResource("testSelectsRollupMetricsBasedOnAggregate.request"),
                        MetricsQuery.class)
        );

        final ArgumentCaptor<MetricsQuery> captor = ArgumentCaptor.forClass(MetricsQuery.class);
        verify(_mockClient, times(1)).queryMetrics(captor.capture());
        final MetricsQuery request = captor.getValue();
        assertEquals(Optional.of(Instant.ofEpochMilli(1)), request.getStartTime());
        assertEquals(1, request.getMetrics().size());
        final Metric metric = request.getMetrics().get(0);
        assertEquals("foo_1h", metric.getName());
        verify(_mockRewrittenMetricsQueryConsumer).accept(notNull());
    }

    @Test
    public void testOnlyUsesRollupsWhenEnabledForOrigin() throws Exception {
        when(_mockClient.queryMetrics(any())).thenReturn(
                CompletableFuture.completedFuture(
                        OBJECT_MAPPER.readValue(
                                readResource("testSelectsRollupMetricsBasedOnAggregate.backend_response"),
                                MetricsQueryResponse.class)
                )
        );

        final QueryContext context = new DefaultQueryContext.Builder()
                .setOrigin(QueryOrigin.ALERT_EVALUATION)
                .build();

        _service.queryMetrics(
                context,
                OBJECT_MAPPER.readValue(
                        readResource("testSelectsRollupMetricsBasedOnAggregate.request"),
                        MetricsQuery.class)
        );

        final ArgumentCaptor<MetricsQuery> captor = ArgumentCaptor.forClass(MetricsQuery.class);
        verify(_mockClient, times(1)).queryMetrics(captor.capture());
        final MetricsQuery request = captor.getValue();
        assertEquals(Optional.of(Instant.ofEpochMilli(1)), request.getStartTime());
        assertEquals(1, request.getMetrics().size());
        final Metric metric = request.getMetrics().get(0);
        assertEquals("foo", metric.getName());
        verify(_mockRewrittenMetricsQueryConsumer).accept(notNull());

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
                TEST_CONTEXT,
                OBJECT_MAPPER.readValue(
                        readResource("testIgnoresRollupsForUnalignedAggregate.request"),
                        MetricsQuery.class)
        );

        final ArgumentCaptor<MetricsQuery> captor = ArgumentCaptor.forClass(MetricsQuery.class);
        verify(_mockClient, times(1)).queryMetrics(captor.capture());
        final MetricsQuery request = captor.getValue();
        assertEquals(Optional.of(Instant.ofEpochMilli(1)), request.getStartTime());
        assertEquals(1, request.getMetrics().size());
        final Metric metric = request.getMetrics().get(0);
        assertEquals("foo", metric.getName());
        verify(_mockRewrittenMetricsQueryConsumer).accept(notNull());
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
                TEST_CONTEXT,
                OBJECT_MAPPER.readValue(
                        readResource("testSelectsSmallestRollupBasedOnAggregate.request"),
                        MetricsQuery.class)
        );

        final ArgumentCaptor<MetricsQuery> captor = ArgumentCaptor.forClass(MetricsQuery.class);
        verify(_mockClient, times(1)).queryMetrics(captor.capture());
        final MetricsQuery request = captor.getValue();
        assertEquals(Optional.of(Instant.ofEpochMilli(1)), request.getStartTime());
        assertEquals(1, request.getMetrics().size());
        final Metric metric = request.getMetrics().get(0);
        assertEquals("foo_1h", metric.getName());
        verify(_mockRewrittenMetricsQueryConsumer).accept(notNull());
    }

    @Test
    public void testIgnoresBlacklistedRollups() throws Exception {
        when(_mockClient.queryMetrics(any())).thenReturn(
                CompletableFuture.completedFuture(
                        OBJECT_MAPPER.readValue(
                                readResource("testIgnoresBlacklistedRollups.backend_response"),
                                MetricsQueryResponse.class
                        )
                )
        );

        when(_mockQueryConfig.getQueryEnabledRollups(any())).thenReturn(ImmutableSet.of(SamplingUnit.HOURS));

        _service.queryMetrics(
                TEST_CONTEXT,
                OBJECT_MAPPER.readValue(
                        readResource("testIgnoresBlacklistedRollups.request"),
                        MetricsQuery.class)
        );

        final ArgumentCaptor<MetricsQuery> captor = ArgumentCaptor.forClass(MetricsQuery.class);
        verify(_mockClient, times(1)).queryMetrics(captor.capture());
        final MetricsQuery request = captor.getValue();
        assertEquals(Optional.of(Instant.ofEpochMilli(1)), request.getStartTime());
        assertEquals(1, request.getMetrics().size());
        final Metric metric = request.getMetrics().get(0);
        assertEquals("expect to use hourly rollup when daily is disabled", "foo_1h", metric.getName());
        verify(_mockRewrittenMetricsQueryConsumer).accept(notNull());
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
                TEST_CONTEXT,
                OBJECT_MAPPER.readValue(
                        readResource("testIgnoresRollupsForSpecialCase.request"),
                        MetricsQuery.class)
        );

        final ArgumentCaptor<MetricsQuery> captor = ArgumentCaptor.forClass(MetricsQuery.class);
        verify(_mockClient, times(1)).queryMetrics(captor.capture());
        final MetricsQuery request = captor.getValue();
        assertEquals(Optional.of(Instant.ofEpochMilli(1)), request.getStartTime());
        assertEquals(1, request.getMetrics().size());
        final Metric metric = request.getMetrics().get(0);
        assertEquals("foo", metric.getName());
        verify(_mockRewrittenMetricsQueryConsumer).accept(notNull());
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

    @Test
    public void testFilterTagNamesOnTagQuery() throws Exception {
        when(_mockClient.queryMetricTags(Mockito.any(TagsQuery.class))).thenReturn(
                CompletableFuture.completedFuture(
                        OBJECT_MAPPER.readValue(
                                readResource("testFilterTagNamesOnTagQuery.backend_response"),
                                MetricsQueryResponse.class
                        )
                )
        );

        final MetricsQueryResponse response = _service.queryMetricTags(
                new TagsQuery.Builder()
                        .setStartTimeRelative(
                                new RelativeDateTime.Builder()
                                        .setUnit(TimeUnit.HOURS)
                                        .setValue(1)
                                        .build())
                        .setMetrics(
                                ImmutableList.of(
                                        new MetricTags.Builder()
                                                .setName("kairosdb.protocol.http_request_count")
                                                .build()
                                ))
                        .build()
        ).toCompletableFuture().get();

        verify(_mockClient).queryMetricTags(Mockito.any(TagsQuery.class));
        assertEquals(1, response.getQueries().size());
        assertEquals(1, response.getQueries().get(0).getResults().size());
        assertEquals(2, response.getQueries().get(0).getResults().get(0).getTags().size());
        assertEquals(
                ImmutableList.of(
                    "query",
                    "tags"
                ),
                response.getQueries().get(0).getResults().get(0).getTags().get("method"));
    }

    @Test
    public void testRollupQueryRewritingBasicRewrite() {
        final ImmutableList<Aggregator> aggregators = ImmutableList.of(
                TestBeanFactory.createAggregatorBuilder()
                        .setSampling(simpleSampling(3, SamplingUnit.HOURS))
                        .setAlignSampling(true)
                        .build()
        );
        final MetricsQuery original = simpleMetricsQuery("my_metric", aggregators);
        final MetricsQuery rewritten = KairosDbServiceImpl.useAvailableRollups(
                ImmutableList.of("my_metric_1h"),
                original,
                s -> ImmutableSet.of(SamplingUnit.HOURS),
                new NoOpMetrics()
        );
        final MetricsQuery expected = simpleMetricsQuery("my_metric_1h", aggregators);
        assertEquals(expected, rewritten);
    }

    @Test
    public void testRollupHonorsOriginalMetricName() {
        final ImmutableList<Aggregator> aggregators = ImmutableList.of(
                TestBeanFactory.createAggregatorBuilder()
                        .setSampling(simpleSampling(3, SamplingUnit.HOURS))
                        .setAlignSampling(true)
                        .build()
        );
        final MetricsQuery original = simpleMetricsQuery("my_metric", aggregators);
        final MetricsQuery rewritten = KairosDbServiceImpl.useAvailableRollups(
                ImmutableList.of("my_metric_with_suffix_1h"),
                original,
                s -> ImmutableSet.of(SamplingUnit.HOURS),
                new NoOpMetrics()
        );
        assertEquals(original, rewritten);
    }

    @Test
    public void testRollupQueryRewritingDoesNotRewriteOddInterval() {
        final MetricsQuery original = simpleMetricsQuery(
                "my_metric",
                ImmutableList.of(
                        TestBeanFactory.createAggregatorBuilder()
                            .setSampling(simpleSampling(90, SamplingUnit.MINUTES))
                            .setAlignSampling(true)
                            .build()
                )
        );
        final MetricsQuery rewritten = KairosDbServiceImpl.useAvailableRollups(
                ImmutableList.of("my_metric_1h"),
                original,
                s -> ImmutableSet.of(SamplingUnit.HOURS),
                new NoOpMetrics()
        );
        assertEquals(original, rewritten);
    }

    @Test
    public void testRollupQueryRewritingDoesNotRewriteUnaligned() {
        final MetricsQuery original = simpleMetricsQuery(
                "my_metric",
                ImmutableList.of(
                        TestBeanFactory.createAggregatorBuilder()
                                .setSampling(simpleSampling(1, SamplingUnit.HOURS))
                                .setAlignSampling(false)
                                .build()
                )
        );
        final MetricsQuery rewritten = KairosDbServiceImpl.useAvailableRollups(
                ImmutableList.of("my_metric_1h"),
                original,
                s -> ImmutableSet.of(SamplingUnit.HOURS),
                new NoOpMetrics()
        );
        assertEquals(original, rewritten);
    }

    @Test
    public void testRollupQueryRewritingDoesNotIgnoreUnaligned() {
        final MetricsQuery original = simpleMetricsQuery(
                "my_metric",
                ImmutableList.of(
                        TestBeanFactory.createAggregatorBuilder()
                            .setSampling(simpleSampling(1, SamplingUnit.MILLISECONDS))
                            .setAlignSampling(false)
                            .build(),
                        TestBeanFactory.createAggregatorBuilder()
                            .setSampling(simpleSampling(1, SamplingUnit.HOURS))
                            .setAlignSampling(true)
                            .build()
                )
        );
        final MetricsQuery rewritten = KairosDbServiceImpl.useAvailableRollups(
                ImmutableList.of("my_metric_1h"),
                original,
                s -> ImmutableSet.of(SamplingUnit.HOURS),
                new NoOpMetrics()
        );
        assertEquals(original, rewritten);
    }

    @Test
    public void testRollupQueryRewritingDoesNotRewriteDisabled() {
        final ImmutableList<Aggregator> aggregators = ImmutableList.of(
                TestBeanFactory.createAggregatorBuilder()
                        .setSampling(simpleSampling(3, SamplingUnit.HOURS))
                        .setAlignSampling(true)
                        .build()
        );
        final MetricsQuery original = simpleMetricsQuery("my_metric", aggregators);
        final MetricsQuery rewritten = KairosDbServiceImpl.useAvailableRollups(
                ImmutableList.of("my_metric_1h"),
                original,
                s -> ImmutableSet.of(),
                new NoOpMetrics()
        );
        final MetricsQuery expected = simpleMetricsQuery("my_metric", aggregators);
        assertEquals(expected, rewritten);
    }

    @Test
    public void testGetCoarsestUsableRollupMetric() {
        final RollupMetric hourly = ThreadLocalBuilder.build(RollupMetric.Builder.class, b -> b
                .setBaseMetricName("my_metric")
                .setPeriod(RollupPeriod.HOURLY)
        );
        final RollupMetric daily = ThreadLocalBuilder.build(RollupMetric.Builder.class, b -> b
                .setBaseMetricName("my_metric")
                .setPeriod(RollupPeriod.DAILY)
        );
        assertEquals(
                Optional.of(hourly),
                KairosDbServiceImpl.getCoarsestUsableRollupMetric(
                        "my_metric",
                        ImmutableList.of(hourly, daily),
                        s -> ImmutableSet.of(SamplingUnit.HOURS),
                        SamplingUnit.HOURS
                )
        );
        assertEquals(
                Optional.empty(),
                KairosDbServiceImpl.getCoarsestUsableRollupMetric(
                        "my_metric",
                        ImmutableList.of(daily),
                        s -> ImmutableSet.of(SamplingUnit.HOURS),
                        SamplingUnit.HOURS
                )
        );
        assertEquals(
                Optional.empty(),
                KairosDbServiceImpl.getCoarsestUsableRollupMetric(
                        "my_metric",
                        ImmutableList.of(hourly),
                        s -> ImmutableSet.of(),
                        SamplingUnit.HOURS
                )
        );
    }

    private Sampling simpleSampling(final int value, final SamplingUnit unit) {
        return new Sampling.Builder().setValue(value).setUnit(unit).build();
    }

    private MetricsQuery simpleMetricsQuery(final String metricName, final ImmutableList<Aggregator> aggregators) {
        return new MetricsQuery.Builder()
                .setMetrics(ImmutableList.of(
                        new Metric.Builder()
                                .setName(metricName)
                                .setAggregators(aggregators)
                                .build()
                ))
                .setStartTime(Instant.EPOCH)
                .build();
    }

    private String readResource(final String resourceSuffix) {
        try {
            return IOUtils.toString(getClass()
                    .getClassLoader()
                    .getResourceAsStream("com/arpnetworking/kairos/service/" + CLASS_NAME + "." + resourceSuffix + ".json"),
                    StandardCharsets.UTF_8);
        } catch (final IOException e) {
            fail("Failed with exception: " + e);
            return null;
        }
    }
}
