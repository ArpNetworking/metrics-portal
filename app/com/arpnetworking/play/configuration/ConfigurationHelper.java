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

import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.typesafe.config.Config;
import play.Environment;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.io.File;

/**
 * Utility methods that provide common patterns when interacting with Play's {@code Config} class.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class ConfigurationHelper {

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

    private ConfigurationHelper() {}

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationHelper.class);
}
