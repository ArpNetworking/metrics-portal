package com.arpnetworking.play.configuration;

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
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
        final Injector injector = Guice.createInjector();

        final Config config = ConfigFactory.parseMap(ImmutableMap.of(
                "type", "com.arpnetworking.play.configuration.ConfigurationHelperTest$InstantiatedClass"
        ));

        final InstantiatedClass result = ConfigurationHelper.getInstance(injector, Environment.simple(), config);
        Assert.assertEquals(config, result.getConfig());
    }

    private static final class InstantiatedClass {
        @Inject
        public InstantiatedClass(@Assisted final Config config) {
            _config = config;
        }

        public Config getConfig() {
            return _config;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof InstantiatedClass)) {
                return false;
            }
            final InstantiatedClass that = (InstantiatedClass) o;
            return _config.equals(that._config);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_config);
        }

        private final Config _config;
    }
}
