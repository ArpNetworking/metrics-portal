/*
 * Copyright 2019 Dropbox, Inc.
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

package com.arpnetworking.metrics.portal.reports.impl.chrome;

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link OriginConfig}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class PerOriginConfigsTest {
    @Test
    public void testDeserialization() throws IOException {
        assertEquals(
                new PerOriginConfigs.Builder()
                        .setByOrigin(ImmutableMap.of(
                                "http://example.com", new OriginConfig.Builder()
                                        .setAllowedNavigationPaths(ImmutableSet.of("/"))
                                        .build()
                        ))
                        .build(),
                MAPPER.readValue(
                        "{\"byOrigin\": {\"http://example.com\": {\"allowedNavigationPaths\": [\"/\"]}}}",
                        PerOriginConfigs.class
                )
        );
    }

    @Test
    public void testDefaults() {
        final PerOriginConfigs emptyConfig = makeSimpleConfig(ImmutableMap.of());
        assertFalse(emptyConfig.isNavigationAllowed("http://example.com"));
        assertFalse(emptyConfig.isRequestAllowed("http://example.com"));
        assertEquals(ImmutableMap.of(), emptyConfig.getAdditionalHeaders("http://example.com"));
    }

    @Test
    public void testDispatch() {
        final PerOriginConfigs config = makeSimpleConfig(ImmutableMap.of(
                "https://example.com", "/",
                "https://google.com", "/search"
        ));
        assertTrue(config.isNavigationAllowed("https://example.com/"));
        assertFalse(config.isNavigationAllowed("http://example.com/"));
        assertFalse(config.isNavigationAllowed("https://example.com"));
        assertFalse(config.isNavigationAllowed("https://example.com/search"));

        assertTrue(config.isNavigationAllowed("https://google.com/search"));
        assertFalse(config.isNavigationAllowed("https://google.com"));
        assertFalse(config.isNavigationAllowed("https://google.com/"));

        assertFalse(config.isNavigationAllowed("https://other.com/"));
    }

    @Test
    public void testIsNavigationAllowed() {
        final OriginConfig config = new OriginConfig.Builder()
                .setAllowedRequestPaths(ImmutableSet.of("/allowed-req-\\d+"))
                .setAllowedNavigationPaths(ImmutableSet.of("/allowed-nav-\\d+"))
                .build();

        assertFalse(config.isNavigationAllowed(""));
        assertFalse(config.isNavigationAllowed("/disallowed"));
        assertFalse(config.isNavigationAllowed("/allowed-req-1"));
        assertTrue(config.isNavigationAllowed("/allowed-nav-1"));

        assertFalse(config.isRequestAllowed("/sneaky-prefix/allowed-nav-1"));
        assertFalse(config.isRequestAllowed("/allowed-nav-1/sneaky-suffix"));
    }

    private PerOriginConfigs makeSimpleConfig(final ImmutableMap<String, String> allowedPathByOrigin) {
        return new PerOriginConfigs.Builder()
                .setByOrigin(
                        allowedPathByOrigin.entrySet().stream().collect(ImmutableMap.toImmutableMap(
                                Map.Entry::getKey,
                                entry -> new OriginConfig.Builder().setAllowedNavigationPaths(ImmutableSet.of(entry.getValue())).build()
                        ))
                )
                .build();
    }

    private static final ObjectMapper MAPPER = ObjectMapperFactory.createInstance();
}
