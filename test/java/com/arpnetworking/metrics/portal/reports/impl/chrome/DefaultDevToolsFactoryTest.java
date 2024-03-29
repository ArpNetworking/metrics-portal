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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigUtil;
import com.typesafe.config.ConfigValueFactory;
import org.junit.Test;

import java.time.format.DateTimeParseException;
import javax.annotation.Nullable;

import static org.junit.Assert.fail;

/**
 * Tests for {@link DefaultDevToolsFactory}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class DefaultDevToolsFactoryTest {

    private static final Config VALID_CONFIG = ConfigFactory.parseMap(ImmutableMap.of(
            "path", "/some/path",
            "args", ImmutableMap.of(
                    "no-sandbox", "true",
                    "headless", "true"
            ),
            "executor", ImmutableMap.of(
                    "corePoolSize", 0,
                    "maximumPoolSize", 8,
                    "keepAlive", "PT1S",
                    "queueSize", 1024
            ),
            "originConfigs", ImmutableMap.of(
                    "byOrigin", ImmutableMap.of(
                            ConfigUtil.quoteString("https://whitelisted.com"), ImmutableMap.of()
                    )
            )
    ));

    @Test
    public void testValidConstruction() {
        new DefaultDevToolsFactory.Builder().setConfig(VALID_CONFIG).build();
    }

    @Test(timeout = 10000)
    public void testInvalidConstruction() {
        final ImmutableSet<String> requiredFields = ImmutableSet.of(
                "path",
                "args"
        );
        requiredFields.forEach(this::assertMissingRaises);
        requiredFields.forEach(field -> assertInvalidates(field, null, ConfigException.Missing.class));

        assertStillValid("networkConfigurationProtocol", "NETWORK");

        final ImmutableSet<String> optionalFields = ImmutableSet.of(
                "executor",
                "executor.corePoolSize",
                "executor.maximumPoolSize",
                "executor.keepAlive",
                "executor.queueSize"
        );
        optionalFields.forEach(field -> new DefaultDevToolsFactory.Builder().setConfig(VALID_CONFIG.withoutPath(field)).build());

        final ImmutableSet<ImmutableList<Object>> invalidations = ImmutableSet.of(
                ImmutableList.of("path", ImmutableMap.of(), ConfigException.WrongType.class),
                ImmutableList.of("args", 1, ConfigException.WrongType.class),
                ImmutableList.of("executor", 1, ConfigException.WrongType.class),
                ImmutableList.of("networkConfigurationProtocol", "SOME_RANDOM_NON_ENUM_NAME", IllegalArgumentException.class),

                ImmutableList.of("executor.corePoolSize", "", ConfigException.WrongType.class),
                ImmutableList.of("executor.corePoolSize", -1, IllegalArgumentException.class),

                ImmutableList.of("executor.maximumPoolSize", "", ConfigException.WrongType.class),
                ImmutableList.of("executor.maximumPoolSize", 0, IllegalArgumentException.class),
                ImmutableList.of("executor.maximumPoolSize", -1, IllegalArgumentException.class),

                ImmutableList.of("executor.keepAlive", ImmutableMap.of(), ConfigException.WrongType.class),
                ImmutableList.of("executor.keepAlive", "not ISO-8601 duration", DateTimeParseException.class),

                ImmutableList.of("executor.queueSize", "", ConfigException.WrongType.class),
                ImmutableList.of("executor.queueSize", 0, IllegalArgumentException.class),
                ImmutableList.of("executor.queueSize", -1, IllegalArgumentException.class)
        );
        invalidations.forEach(this::assertInvalidates);
    }

    @Test
    public void testAssertMissingRaises() {
        try {
            assertMissingRaises("path");
            fail("missing a required field should have raised");
        } catch (final AssertionError e) {
        }
    }

    @Test
    public void testAssertInvalidates() {
        try {
            assertInvalidates("path", ImmutableMap.of(), ConfigException.WrongType.class);
        } catch (final AssertionError e) {
            fail("setting path={} should have raised");
        }

        try {
            assertInvalidates("path", "/valid/path", Exception.class);
            fail("setting a field to a valid value should not have invalidated the config");
        } catch (final AssertionError e) {
        }
    }

    private void assertMissingRaises(final String field) {
        try {
            new DefaultDevToolsFactory.Builder().setConfig(VALID_CONFIG.withoutPath(field)).build();
            fail("missing field '" + field + "' should have made constructor fail");
        } catch (final ConfigException.Missing e) {
        }
    }

    private <E extends Exception> void assertStillValid(final String field, @Nullable final Object value) {
        new DefaultDevToolsFactory.Builder().setConfig(VALID_CONFIG.withValue(field, ConfigValueFactory.fromAnyRef(value))).build();
    }

    private <E extends Exception> void assertInvalidates(final String field, @Nullable final Object badValue, final Class<E> clazz) {
        try {
            new DefaultDevToolsFactory.Builder().setConfig(VALID_CONFIG.withValue(field, ConfigValueFactory.fromAnyRef(badValue))).build();
            fail("setting " + field + "=" + badValue + " should have made constructor throw " + clazz.getName());
            // CHECKSTYLE.OFF: IllegalCatch
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            if (!clazz.isInstance(e)) {
                fail(
                        "setting " + field + "=" + badValue
                                + " should have made constructor throw " + clazz.getName()
                                + ", not " + e.getClass().getName()
                );
            }
        }
    }

    private void assertInvalidates(final ImmutableList<Object> fieldValueException) {
        final String field = (String) fieldValueException.get(0);
        final Object value = fieldValueException.get(1);
        @SuppressWarnings("unchecked")
        final Class<? extends Exception> clazz = (Class<? extends Exception>) fieldValueException.get(2);
        assertInvalidates(field, value, clazz);
    }

}
