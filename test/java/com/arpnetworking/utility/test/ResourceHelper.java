/*
 * Copyright 2019 Dropbox Inc.
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
package com.arpnetworking.utility.test;

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import java.io.IOException;
import java.net.URL;

/**
 * Helper to load resources.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class ResourceHelper {

    /**
     * Load a resource associated with a particular class.
     *
     * E.g. for class {@code foo.bar.Baz}, with suffix {@code quux}, the resource will live at {@code foo/bar/Baz.quux.json}.
     *
     * @param object An instance of the test-class that owns the resource.
     * @param suffix A resource identifier appended to the class's basename.
     * @return The contents of that resource-file, as a string.
     * @throws IllegalArgumentException if the resource does not exist.
     * @throws IOException if the resource can't be loaded.
     */
    public static String loadResource(final Object object, final String suffix) throws IOException {
        return loadResource(object.getClass(), suffix);
    }

    /**
     * Load a resource associated with a particular class.
     *
     * E.g. for class {@code foo.bar.Baz}, with suffix {@code quux}, the resource will live at {@code foo/bar/Baz.quux.json}.
     *
     * @param testClass The test-class that owns the resource.
     * @param suffix A resource identifier appended to the class's basename.
     * @return The contents of that resource-file, as a string.
     * @throws IllegalArgumentException if the resource does not exist.
     * @throws IOException if the resource can't be loaded.
     */
    public static String loadResource(final Class<?> testClass, final String suffix) throws IOException {
        final URL resourceUrl = resourceURL(testClass, suffix);
        return Resources.toString(resourceUrl, Charsets.UTF_8);
    }

    /**
     * Get the URL for a resource associated with a particular class.
     *
     * E.g. for class {@code foo.bar.Baz}, with suffix {@code quux}, the resource will live at {@code foo/bar/Baz.quux.json}.
     *
     * @param testClass The test-class that owns the resource.
     * @param suffix A resource identifier appended to the class's basename.
     * @return A URL for that resource-file.
     * @throws IllegalArgumentException if the resource does not exist.
     */
    public static URL resourceURL(final Class<?> testClass, final String suffix) {
        final String resourcePath = testClass.getCanonicalName().replace(".", "/")
                + "."
                + suffix
                + ".json";
        final URL resourceUrl = testClass.getClassLoader().getResource(resourcePath);
        if (resourceUrl == null) {
            throw new IllegalArgumentException(String.format("Resource not found: %s", resourcePath));
        }
        return resourceUrl;
    }

    /**
     * Load a resource associated with a particular class (see {@code loadResource}), deserialized using Jackson.
     *
     * @param <T> The type of object to deserialize.
     * @param testClass The test-class that owns the resource.
     * @param suffix A resource identifier appended to the class's basename.
     * @param clazz The type of object to deserialize.
     * @return The contents of that resource-file.
     * @throws IOException if the resource can't be loaded, or if deserialization fails.
     */
    public static <T> T loadResourceAs(final Class<?> testClass, final String suffix, final Class<? extends T> clazz) throws IOException {
        return ObjectMapperFactory.getInstance().readValue(loadResource(testClass, suffix), clazz);
    }

    private ResourceHelper() {}

}
