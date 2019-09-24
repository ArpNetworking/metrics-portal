package com.arpnetworking.kairos.client.models.testing;

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SerializationTestUtils {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();

    public static void assertDeserializationLosesNothing(final ObjectNode node, final Class<?> clazz) {
        final Object complete = OBJECT_MAPPER.convertValue(node, clazz);
        for (final String field : ImmutableList.copyOf(node.fieldNames())) {
            try {
                final Object diminished = OBJECT_MAPPER.convertValue(node.deepCopy().without(field), clazz);
                assertNotEquals(String.format("removing field %s did not change deserialized representation", field), complete, diminished);
            } catch (final IllegalArgumentException e) {
            }
        }
    }

    public static <T> void assertSerializationLosesNothing(final T o, final Class<T> clazz) throws IOException {
        assertEquals(o, OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(o), clazz));
    }

    public static <T> void assertTranslationLosesNothing(final ObjectNode node, final Class<T> clazz) throws IOException {
        assertDeserializationLosesNothing(node, clazz);
        final T deserialized = OBJECT_MAPPER.convertValue(node, clazz);
        assertSerializationLosesNothing(deserialized, clazz);
    }
}
