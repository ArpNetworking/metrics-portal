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
                "model", "Civic"
        ));

        final InstantiatedObject result = ConfigurationHelper.getInstance(Guice.createInjector(), Environment.simple(), config);
        Assert.assertEquals("Civic", result.getModel());
    }

    private static final class InstantiatedObject {
        @Inject
        public InstantiatedObject(@Assisted final Config config) {
            _model = config.getString("model");
        }

        public String getModel() {
            return _model;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof InstantiatedObject)) {
                return false;
            }
            final InstantiatedObject that = (InstantiatedObject) o;
            return _model.equals(that._model);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_model);
        }

        private final String _model;
    }
}
