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
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;

/**
 * Tests for {@link MetricsQueryResponse.QueryResult}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class MetricsQueryResponseQueryResultTest {

    @Test
    public void testTranslationLosesNothing() throws Exception {
        SerializationTestUtils.assertTranslationLosesNothing(
                ResourceHelper.loadResource(getClass(), "testTranslationLosesNothing"),
                MetricsQueryResponse.QueryResult.class
        );
    }

    @Test
    public void testBuilder() throws InvocationTargetException, IllegalAccessException {
        BuildableTestHelper.testBuild(
                new MetricsQueryResponse.QueryResult.Builder()
                        .setTags(ImmutableListMultimap.of("browser", "Chrome"))
                        .setName("name")
                        .setValues(ImmutableList.of(
                                new MetricsQueryResponse.DataPoint.Builder()
                                        .setValue(1.23)
                                        .setTime(Instant.now())
                                        .build()))
                        .setGroupBy(ImmutableList.of(
                                new MetricsQueryResponse.QueryTagGroupBy.Builder()
                                        .setTags(ImmutableList.of("operating_system"))
                                        .setGroup(ImmutableMap.of("operating_system", "windows"))
                                        .build()))
                        .setOtherArgs(ImmutableMap.of("foo", "bar")),
                MetricsQueryResponse.QueryResult.class);
    }

    @Test
    public void testEquality() throws InvocationTargetException, IllegalAccessException {
        EqualityTestHelper.testEquality(
                new MetricsQueryResponse.QueryResult.Builder()
                        .setTags(ImmutableListMultimap.of("browser", "Chrome"))
                        .setName("name")
                        .setValues(ImmutableList.of(
                                new MetricsQueryResponse.DataPoint.Builder()
                                        .setValue(1.23)
                                        .setTime(Instant.now())
                                        .build()))
                        .setGroupBy(ImmutableList.of(
                                new MetricsQueryResponse.QueryTagGroupBy.Builder()
                                        .setTags(ImmutableList.of("operating_system"))
                                        .setGroup(ImmutableMap.of("operating_system", "windows"))
                                        .build()))
                        .setOtherArgs(ImmutableMap.of("foo", "bar")),
                new MetricsQueryResponse.QueryResult.Builder()
                        .setTags(ImmutableListMultimap.of("country", "Canada"))
                        .setName("other_name")
                        .setValues(ImmutableList.of(
                                new MetricsQueryResponse.DataPoint.Builder()
                                        .setValue(2.46)
                                        .setTime(Instant.now().plusSeconds(60))
                                        .build()))
                        .setGroupBy(ImmutableList.of(
                                new MetricsQueryResponse.QueryTagGroupBy.Builder()
                                        .setTags(ImmutableList.of("operating_system"))
                                        .setGroup(ImmutableMap.of("operating_system", "linux"))
                                        .build()))
                        .setOtherArgs(ImmutableMap.of("foo2", "bar2")),
                MetricsQueryResponse.QueryResult.class);
    }
}
