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
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;

/**
 * Tests for {@link TagsQuery}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class TagsQueryTest {

    @Test
    public void testTranslationLosesNothing() throws Exception {
        SerializationTestUtils.assertTranslationLosesNothing(
                ResourceHelper.loadResource(getClass(), "testTranslationLosesNothing"),
                TagsQuery.class);
    }

    @Test
    public void testStartRelative() throws Exception {
        SerializationTestUtils.assertTranslationLosesNothing(
                ResourceHelper.loadResource(getClass(), "testStartRelative"),
                TagsQuery.class);
    }

    @Test
    public void tesEndRelative() throws Exception {
        SerializationTestUtils.assertTranslationLosesNothing(
                ResourceHelper.loadResource(getClass(), "testEndRelative"),
                TagsQuery.class);
    }

    @Test
    public void testNoEndTime() throws Exception {
        SerializationTestUtils.assertTranslationLosesNothing(
                ResourceHelper.loadResource(getClass(), "testNoEndTime"),
                TagsQuery.class);
    }

    // Ideally, we would have builder/equality tests, but those test-helpers assume that the builder
    //     (a) has a 1:1 correspondence between setters and fields, and
    //     (b) has no interdependencies between its fields
    //   both of which this class violates, because the KairosDB API violates it.
}
