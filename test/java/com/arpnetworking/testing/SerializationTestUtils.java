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
package com.arpnetworking.testing;

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;

import java.io.IOException;

/**
 * Utilities to help test JSON de/serialization of objects.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class SerializationTestUtils {

    // TODO(spencerpearson): allow the ObjectMapper to get passed in, if ever necessary
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();

    /**
     * Assert that two JSON values are semantically equivalent. (Map order doesn't matter, array order does.)
     *
     * @param expected The string encoding the expected JSON value.
     * @param actual The string encoding the actual JSON value.
     * @throws IOException If the given strings are not valid JSON.
     */
    public static void assertJsonEquals(final String expected, final String actual) throws IOException {
        Assert.assertEquals(OBJECT_MAPPER.readTree(expected), OBJECT_MAPPER.readTree(actual));
    }

    /**
     * Assert that deserializing some JSON and re-serializing it results in exactly the same JSON (semantically).
     *
     * @param json The JSON representation of an object.
     * @param clazz The class to deserialize it into.
     * @throws IOException If something goes wrong during deserialization.
     */
    public static void assertTranslationLosesNothing(final String json, final Class<?> clazz) throws IOException {
        assertJsonEquals(json, OBJECT_MAPPER.writeValueAsString(OBJECT_MAPPER.readValue(json, clazz)));
    }

    private SerializationTestUtils() {}
}
