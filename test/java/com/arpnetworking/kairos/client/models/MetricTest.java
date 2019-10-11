/*
 * Copyright 2019 Dropbox, Inc
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
package com.arpnetworking.kairos.client.models;

import com.arpnetworking.commons.test.BuildableTestHelper;
import com.arpnetworking.commons.test.EqualityTestHelper;
import com.arpnetworking.testing.SerializationTestUtils;
import com.arpnetworking.utility.test.ResourceHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;

/**
 * Tests for {@link Metric}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class MetricTest {

    @Test
    public void testTranslationLosesNothing() throws Exception {
        SerializationTestUtils.assertTranslationLosesNothing(
                ResourceHelper.loadResource(getClass(), "testTranslationLosesNothing"),
                Metric.class
        );
    }

    @Test
    public void testBuilder() throws InvocationTargetException, IllegalAccessException {
        BuildableTestHelper.testBuild(
                new Metric.Builder()
                        .setName("metricName")
                        .setAggregators(ImmutableList.of(
                                new Aggregator.Builder()
                                        .setName("sum")
                                        .setAlignSampling(true)
                                        .setSampling(
                                                new Sampling.Builder()
                                                        .setValue(1)
                                                        .setUnit(SamplingUnit.HOURS)
                                                        .build())
                                        .build()))
                        .setGroupBy(ImmutableList.of(
                                new MetricsQuery.GroupBy.Builder()
                                        .setName("host")
                                        .build()))
                        .setLimit(1)
                        .setOrder(Metric.Order.DESC)
                        .setTags(ImmutableMultimap.of("tag", "value"))
                        .setOtherArgs(ImmutableMap.of("foo", "bar")),
                Metric.class);
    }

    @Test
    public void testEquality() throws InvocationTargetException, IllegalAccessException {
        EqualityTestHelper.testEquality(
                new Metric.Builder()
                        .setName("metricName")
                        .setAggregators(ImmutableList.of(
                                new Aggregator.Builder()
                                        .setName("sum")
                                        .setAlignSampling(true)
                                        .setSampling(
                                                new Sampling.Builder()
                                                        .setValue(1)
                                                        .setUnit(SamplingUnit.HOURS)
                                                        .build())
                                        .build()))
                        .setGroupBy(ImmutableList.of(
                                new MetricsQuery.GroupBy.Builder()
                                        .setName("host")
                                        .build()))
                        .setLimit(1)
                        .setOrder(Metric.Order.DESC)
                        .setTags(ImmutableMultimap.of("tag", "value"))
                        .setOtherArgs(ImmutableMap.of("foo", "bar")),
                new Metric.Builder()
                        .setName("metricName2")
                        .setAggregators(ImmutableList.of(
                                new Aggregator.Builder()
                                        .setName("count")
                                        .setAlignSampling(true)
                                        .setSampling(
                                                new Sampling.Builder()
                                                        .setValue(1)
                                                        .setUnit(SamplingUnit.HOURS)
                                                        .build())
                                        .build()))
                        .setGroupBy(ImmutableList.of(
                                new MetricsQuery.GroupBy.Builder()
                                        .setName("country")
                                        .build()))
                        .setLimit(99)
                        .setOrder(Metric.Order.ASC)
                        .setTags(ImmutableMultimap.of("tag2", "value2"))
                        .setOtherArgs(ImmutableMap.of("foo2", "bar2")),
                Metric.class);
    }
}
