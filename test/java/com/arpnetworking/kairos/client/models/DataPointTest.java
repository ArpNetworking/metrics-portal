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
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;

/**
 * Tests for {@link DataPoint}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class DataPointTest {

    @Test
    public void testTranslationLosesNothingFromArrayValueNumber() throws Exception {
        SerializationTestUtils.assertTranslationLosesNothing(
                ResourceHelper.loadResource(getClass(), "testTranslationLosesNothingFromArrayValueNumber"),
                DataPoint.class
        );
    }

    @Test
    public void testTranslationLosesNothingFromArrayValueString() throws Exception {
        SerializationTestUtils.assertTranslationLosesNothing(
                ResourceHelper.loadResource(getClass(), "testTranslationLosesNothingFromArrayValueString"),
                DataPoint.class
        );
    }

    @Test
    public void testTranslationLosesNothingFromArrayValueObject() throws Exception {
        SerializationTestUtils.assertTranslationLosesNothing(
                ResourceHelper.loadResource(getClass(), "testTranslationLosesNothingFromArrayValueObject"),
                DataPoint.class
        );
    }

    @Test
    public void testTranslationEquivalencyFromObjectValueNumber() throws Exception {
        SerializationTestUtils.assertTranslationEquivalent(
                ResourceHelper.loadResource(getClass(), "testTranslationLosesNothingFromArrayValueNumber"),
                ResourceHelper.loadResource(getClass(), "testTranslationEquivalencyFromObjectValueNumber"),
                DataPoint.class
        );
    }

    @Test
    public void testTranslationEquivalencyFromObjectValueString() throws Exception {
        SerializationTestUtils.assertTranslationEquivalent(
                ResourceHelper.loadResource(getClass(), "testTranslationLosesNothingFromArrayValueNumber"),
                ResourceHelper.loadResource(getClass(), "testTranslationEquivalencyFromObjectValueNumber"),
                DataPoint.class
        );
    }

    @Test
    public void testTranslationEquivalencyFromObjectValueObject() throws Exception {
        SerializationTestUtils.assertTranslationEquivalent(
                ResourceHelper.loadResource(getClass(), "testTranslationLosesNothingFromArrayValueNumber"),
                ResourceHelper.loadResource(getClass(), "testTranslationEquivalencyFromObjectValueNumber"),
                DataPoint.class
        );
    }

    @Test
    public void testBuilder() throws InvocationTargetException, IllegalAccessException {
        BuildableTestHelper.testBuild(
                new DataPoint.Builder()
                        .setTime(Instant.now())
                        .setValue(new Object()),
                DataPoint.class);
    }

    @Test
    public void testEquality() throws InvocationTargetException, IllegalAccessException {
        EqualityTestHelper.testEquality(
                new DataPoint.Builder()
                        .setTime(Instant.now())
                        .setValue(new Object()),
                new DataPoint.Builder()
                        .setTime(Instant.now().plusSeconds(60))
                        .setValue(new Object()),
                DataPoint.class);
    }
}
