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

import akka.actor.ActorSystem;
import com.arpnetworking.commons.jackson.databind.EnumerationDeserializer;
import com.arpnetworking.commons.jackson.databind.EnumerationDeserializerStrategyUsingToUpperCase;
import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.commons.jackson.databind.module.akka.AkkaModule;
import com.arpnetworking.kairos.client.models.Metric;
import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.arpnetworking.kairos.client.models.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.junit.Assert;
import play.api.libs.json.JsonParserSettings;
import play.api.libs.json.jackson.PlayJsonModule;

import java.io.IOException;

/**
 * Utilities to help test JSON de/serialization of objects.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class SerializationTestUtils {

    // TODO(spencerpearson): allow the ObjectMapper to get passed in, if ever necessary
    // IMPORTANT: The configuration here must match that in MainModule for testing REST APIs
    private static final ObjectMapper OBJECT_MAPPER = createApiObjectMapper();

    /**
     * Create a new configured {@link ObjectMapper} instance for use cases which require
     * additional customization.
     *
     * @return new configured {@link ObjectMapper} instance
     */
    public static ObjectMapper createApiObjectMapper() {
        return createApiObjectMapper(null);
    }

    public static ObjectMapper createApiObjectMapper(@Nullable final ActorSystem actorSystem) {
        final SimpleModule customModule = new SimpleModule();
        customModule.addDeserializer(
                TimeUnit.class,
                EnumerationDeserializer.newInstance(
                        TimeUnit.class,
                        EnumerationDeserializerStrategyUsingToUpperCase.newInstance()));
        customModule.addDeserializer(
                SamplingUnit.class,
                EnumerationDeserializer.newInstance(
                        SamplingUnit.class,
                        EnumerationDeserializerStrategyUsingToUpperCase.newInstance()));
        customModule.addDeserializer(
                Metric.Order.class,
                EnumerationDeserializer.newInstance(
                        Metric.Order.class,
                        EnumerationDeserializerStrategyUsingToUpperCase.newInstance()));
        final ObjectMapper objectMapper = ObjectMapperFactory.createInstance();
        objectMapper.registerModule(new PlayJsonModule(JsonParserSettings.apply()));
        objectMapper.registerModule(customModule);
        if (actorSystem != null) {
            objectMapper.registerModule(new AkkaModule(actorSystem));
        }
        return objectMapper;
    }

    /**
     * The {@link ObjectMapper} configured for serializing/deserializing API requests.
     *
     * @return the {@link ObjectMapper} configured for serializing/deserializing API requests
     */
    public static ObjectMapper getApiObjectMapper() {
        return OBJECT_MAPPER;
    }

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
        assertTranslationEquivalent(json, json, clazz);
    }

    /**
     * Assert that deserializing some JSON and re-serializing it results in the expected JSON (semantically).
     *
     * @param expectedJson The expected JSON representation of an object.
     * @param inputJson The given JSON representation of an object.
     * @param clazz The class to deserialize it into.
     * @throws IOException If something goes wrong during deserialization.
     */
    public static void assertTranslationEquivalent(
            final String expectedJson,
            final String inputJson,
            final Class<?> clazz)
            throws IOException {
        assertJsonEquals(expectedJson, OBJECT_MAPPER.writeValueAsString(OBJECT_MAPPER.readValue(inputJson, clazz)));
    }

    private SerializationTestUtils() {}
}
