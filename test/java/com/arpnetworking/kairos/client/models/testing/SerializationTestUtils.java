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
package com.arpnetworking.kairos.client.models.testing;

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
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
     * Assert that removing any field from an object causes it to deserialize differently (or not at all).
     *
     * @param node The JSON representation of the object.
     * @param clazz The class to deserialize into.
     */
    public static void assertDeserializationLosesNothing(final ObjectNode node, final Class<?> clazz) {
        final Object complete = OBJECT_MAPPER.convertValue(node, clazz);
        for (final String field : ImmutableList.copyOf(node.fieldNames())) {
            try {
                final Object diminished = OBJECT_MAPPER.convertValue(node.deepCopy().without(field), clazz);
                Assert.assertNotEquals(
                        String.format("removing field %s did not change deserialized representation", field),
                        complete,
                        diminished
                );
            } catch (final IllegalArgumentException e) {
            }
        }
    }

    /**
     * Assert that serializing an object and then deserializing it loses no information.
     *
     * @param o The object to de/serialize.
     * @param clazz The class to deserialize into.
     * @param <T> The type of object being de/serialized.
     * @throws IOException If something goes wrong during deserialization.
     */
    public static <T> void assertSerializationLosesNothing(final T o, final Class<T> clazz) throws IOException {
        Assert.assertEquals(o, OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(o), clazz));
    }

    /**
     * Assert that neither deserialization nor serialization loses information.
     *
     * @param node The JSON representation of an object.
     * @param clazz The class to deserialize it into.
     * @param <T> The type of object being de/serialized.
     * @throws IOException If something goes wrong during deserialization.
     */
    public static <T> void assertTranslationLosesNothing(final ObjectNode node, final Class<T> clazz) throws IOException {
        assertDeserializationLosesNothing(node, clazz);
        final T deserialized = OBJECT_MAPPER.convertValue(node, clazz);
        assertSerializationLosesNothing(deserialized, clazz);
    }

    private SerializationTestUtils() {}
}
