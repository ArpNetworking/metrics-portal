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
package com.arpnetworking.utility;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.apache.http.HttpEntity;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.fail;

/**
 * Helper to load resources.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class ResourceHelper {

    public static String loadResource(final Class<?> testClass, final String suffix) {
        final String resourcePath = testClass.getCanonicalName().replace(".", "/")
                + "."
                + suffix
                + ".json";
        final URL resourceUrl = testClass.getClassLoader().getResource(resourcePath);
        if (resourceUrl == null) {
            throw new IllegalArgumentException(String.format("Resource not found: %s", resourcePath));
        }
        try {
            return Resources.toString(resourceUrl, Charsets.UTF_8);
        } catch (final IOException e) {
            fail("Failed with exception: " + e);
            return null;
        }
    }

    public static HttpEntity createEntity(final Class<?> testClass, final String resourceSuffix) {
        try {
            return new StringEntity(loadResource(testClass, resourceSuffix));
        } catch (final IOException e) {
            fail("Failed with exception: " + e);
            return null;
        }
    }

    private ResourceHelper() {}

}
