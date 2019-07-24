package com.arpnetworking.metrics.portal.reports.impl.chrome;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.junit.Test;

import java.time.format.DateTimeParseException;

import javax.annotation.Nullable;

import static org.junit.Assert.fail;

public class DefaultDevToolsFactoryTest {

    private static final Config VALID_CONFIG = ConfigFactory.parseMap(ImmutableMap.of(
            "path", "some path",
            "args", ImmutableMap.of(
                    "no-sandbox", "true",
                    "headless", "true"
            ),
            "executor", ImmutableMap.of(
                    "corePoolSize", 0,
                    "maximumPoolSize", 8,
                    "keepAlive", "PT1S",
                    "queueSize", 1024
            )
    ));

    @Test
    public void testValidConstruction() {
        new DefaultDevToolsFactory(VALID_CONFIG);
    }

    @Test
    public void testInvalidConstruction() {
        final ImmutableSet<String> requiredFields = ImmutableSet.of(
                "path",
                "args",
                "executor",
                "executor.corePoolSize",
                "executor.maximumPoolSize",
                "executor.keepAlive",
                "executor.queueSize"
        );
        requiredFields.forEach(this::assertMissingRaises);
        requiredFields.forEach(field -> assertInvalidates(field, null, ConfigException.Missing.class));

        final ImmutableSet<ImmutableList<Object>> invalidations = ImmutableSet.of(
                ImmutableList.of("path", ImmutableMap.of(), ConfigException.WrongType.class),
                ImmutableList.of("args", 1, ConfigException.WrongType.class),
                ImmutableList.of("executor", 1, ConfigException.WrongType.class),
                ImmutableList.of("executor.corePoolSize", "", ConfigException.WrongType.class),
                ImmutableList.of("executor.maximumPoolSize", 1, ConfigException.WrongType.class),
                ImmutableList.of("executor.keepAlive", ImmutableMap.of(), ConfigException.WrongType.class),
                ImmutableList.of("executor.keepAlive", "not ISO-8601 duration", DateTimeParseException.class),
                ImmutableList.of("executor.queueSize", "", ConfigException.WrongType.class)
        );
        invalidations.forEach(this::assertInvalidates);
    }

    private void assertMissingRaises(final String field) {
        try {
            new DefaultDevToolsFactory(VALID_CONFIG.withoutPath(field));
            fail("missing field '" + field + "' should have made constructor fail");
        } catch (final ConfigException.Missing e) {
        }
    }

    private <E extends Exception> void assertInvalidates(final String field, @Nullable final Object badValue, Class<E> clazz) {
        try {
            new DefaultDevToolsFactory(VALID_CONFIG.withoutPath(field));
            fail("setting " + field + "=" + badValue + " should have made constructor throw " + clazz.getName());
        } catch (final Exception e) {
            if (!clazz.isInstance(e)) {
                fail("setting " + field + "=" + badValue + " should have made constructor throw " + clazz.getName() + ", not " + e.getClass().getName());
            }
        }
    }

    private void assertInvalidates(final ImmutableList<Object> fieldValueException) {
        final String field = (String) fieldValueException.get(0);
        final Object value = fieldValueException.get(1);
        @SuppressWarnings("unchecked")
        final Class<? extends Exception> clazz = (Class<? extends Exception>) fieldValueException.get(2);
    }

}
