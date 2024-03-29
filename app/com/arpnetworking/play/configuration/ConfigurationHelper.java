/*
 * Copyright 2014 Groupon.com
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
package com.arpnetworking.play.configuration;

import com.arpnetworking.notcommons.java.time.TimeAdapters;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.arpnetworking.utility.ConfigurationOverrideModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Injector;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigRenderOptions;
import play.Environment;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.io.File;
import java.io.IOException;

/**
 * Utility methods that provide common patterns when interacting with Play's {@code Config} class.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class ConfigurationHelper {

    private static final String TYPE_KEY = "type";

    /**
     * Return the value of a configuration key as a {@code File} instance.
     *
     * @param configuration Play {@code Config} instance.
     * @param key The name of the configuration key to interpret as a {@code File} reference.
     * @param environment Instance of Play {@code Environment}.
     * @return Instance of {@code File} as defined by key in configuration.
     */
    public static File getFile(final Config configuration, final String key, final Environment environment) {
        final String pathAsString = configuration.getString(key);
        if (!pathAsString.startsWith("/")) {
            return environment.getFile(pathAsString);
        }
        return new File(pathAsString);
    }

    /**
     * Return the value of a configuration key as a {@code FiniteDuration} instance.
     *
     * @param configuration Play {@code Config} instance.
     * @param key The name of the configuration key to interpret as a {@code FiniteDuration} reference.
     * @return Instance of {@code FiniteDuration} as defined by key in configuration.
     */
    public static FiniteDuration getFiniteDuration(final Config configuration, final String key) {
        final Duration duration = Duration.create(configuration.getString(key));
        return new FiniteDuration(
                duration.length(),
                duration.unit());
    }

    /**
     * Return the value of a configuration key as a {@code java.time.Duration} instance.
     *
     * @param configuration Play {@code Config} instance.
     * @param key The name of the configuration key to interpret as a {@code java.time.Duration} reference.
     * @return Instance of {@code java.time.Duration} as defined by key in configuration.
     */
    public static java.time.Duration getJavaDuration(final Config configuration, final String key) {
        final Duration duration = getFiniteDuration(configuration, key);
        return java.time.Duration.of(duration.length(), TimeAdapters.toChronoUnit(duration.unit()));
    }

    /**
     * Return the value of a configuration key as a {@code Class} instance.
     *
     * @param environment Play {@code Environment} instance.
     * @param configuration Play {@code Config} instance.
     * @param key The name of the configuration key to interpret as a {@code Class} reference.
     * @param <T> The type parameter for the {@code Class} instance to return.
     * @return Instance of {@code Class} as defined by key in configuration.
     */
    public static <T> Class<? extends T> getType(
            final Environment environment,
            final Config configuration,
            final String key) {
        final String className = configuration.getString(key);
        try {
            @SuppressWarnings("unchecked")
            final Class<? extends T> clazz = (Class<? extends T>) environment.classLoader().loadClass(className);
            LOGGER.debug()
                    .setMessage("Loaded class")
                    .addData("classLoader", environment.classLoader())
                    .addData("className", className)
                    .log();
            return clazz;
        } catch (final ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Use Guice to instantiate a POJO from configuration.
     *
     * @param injector The injector to use to create the object.
     * @param environment Play {@link Environment} instance.
     * @param configuration The config to instantiate the object from. (Note that this should almost certainly <i>not</i> be
     *   the entire Play {@link Config} instance; instead it is some sub-object describing the Java object to instantiate.
     * @param <T> The type of object to instantiate.
     * @return The instantiated object.
     */
    public static <T> T toInstance(
            final Injector injector,
            final Environment environment,
            final Config configuration) {
        final Class<? extends T> clazz = getType(environment, configuration, TYPE_KEY);
        return injector
                .createChildInjector(new ConfigurationOverrideModule(configuration))
                .getInstance(clazz);
    }

    /**
     * Use Guice to instantiate a POJO from configuration, deserializing directly from the
     * configuration object.
     * <br>
     * If you need more than one level of polymorphic deserialization, such as with
     * objects constructed via composition, you will need to ensure the interface
     * type is properly annotated with {@link com.fasterxml.jackson.annotation.JsonTypeInfo}.
     * If not, Jackson will be unable to resolve the inner type.
     *
     * @param mapper The object mapper instance.
     * @param clazz The type to deserialize
     * @param configuration The config to instantiate the object from. (Note that this should almost certainly <i>not</i> be
     *   the entire Play {@link Config} instance; instead it is some sub-object describing the Java object to instantiate.
     * @param <T> The type of object to instantiate.
     * @return The instantiated object.
     */
    public static <T> T toInstanceMapped(
            final Class<T> clazz,
            final ObjectMapper mapper,
            final Config configuration) {
        final String json = configuration.root().render(ConfigRenderOptions.concise());
        try {
            return mapper.readValue(json, clazz);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Resolves and converts a piece of a {@link Config} to a JSON string.
     *
     * @param config The config to be rendered as JSON. Must be resolved (see {@link Config#resolve}).
     * @param path The path to the sub-object to render as JSON.
     * @return The JSON representation of that sub-object.
     */
    public static String toJson(final Config config, final String path) {
        return config.getValue(path).render(ConfigRenderOptions.concise());
    }

    private ConfigurationHelper() {}

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationHelper.class);
}
