package com.arpnetworking.play.configuration;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Assert;
import org.junit.Test;
import play.Environment;

import java.util.Objects;

public class ConfigurationHelperTest {
    @Test
    public void testGetInstance() {
        final Config config = ConfigFactory.parseMap(ImmutableMap.of(
                "type", "com.arpnetworking.play.configuration.ConfigurationHelperTest$InstantiatedObject",
                "name", "Harold"
        ));

        final InstantiatedObject result = ConfigurationHelper.getInstance(Guice.createInjector(), Environment.simple(), config);
        Assert.assertEquals("Harold", result.getName());
    }

    private static final class InstantiatedObject {
        @Inject
        public InstantiatedObject(@Assisted final Config config) {
            _name = config.getString("name");
        }

        public String getName() {
            return _name;
        }

        private final String _name;
    }
}
