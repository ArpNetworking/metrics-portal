/*
 * Copyright 2018 Dropbox, Inc
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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Assert;
import org.junit.Test;
import play.Environment;

/**
 * Tests for {@link ConfigurationHelper}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class ConfigurationHelperTest {
    @Test
    public void testGetInstance() {
        final Config config = ConfigFactory.parseMap(ImmutableMap.of(
                "type", "com.arpnetworking.play.configuration.ConfigurationHelperTest$InstantiatedObject",
                "name", "Harold"
        ));

        final InstantiatedObject result = ConfigurationHelper.toInstance(Guice.createInjector(), Environment.simple(), config);
        Assert.assertEquals("Harold", result.getName());
    }

    private static final class InstantiatedObject {
        @Inject
        InstantiatedObject(@Assisted final Config config) {
            _name = config.getString("name");
        }

        public String getName() {
            return _name;
        }

        private final String _name;
    }
}
