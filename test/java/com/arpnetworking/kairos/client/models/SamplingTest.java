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
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;

/**
 * Tests for {@link Sampling}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class SamplingTest {

    @Test
    public void testTranslationLosesNothing() throws Exception {
        SerializationTestUtils.assertTranslationLosesNothing(
                ResourceHelper.loadResource(getClass(), "testTranslationLosesNothing"),
                Sampling.class
        );
    }

    @Test
    public void testBuilder() throws InvocationTargetException, IllegalAccessException {
        BuildableTestHelper.testBuild(
                new Sampling.Builder()
                        .setValue(1)
                        .setUnit(SamplingUnit.HOURS)
                        .setOtherArgs(ImmutableMap.of("foo", "bar")),
                Sampling.class);
    }

    @Test
    public void testEquality() throws InvocationTargetException, IllegalAccessException {
        EqualityTestHelper.testEquality(
                new Sampling.Builder()
                        .setValue(1)
                        .setUnit(SamplingUnit.HOURS)
                        .setOtherArgs(ImmutableMap.of("foo", "bar")),
                new Sampling.Builder()
                        .setValue(2)
                        .setUnit(SamplingUnit.DAYS)
                        .setOtherArgs(ImmutableMap.of("foo2", "bar2")),
                Sampling.class);
    }
}
