/*
 * Copyright 2018 Smartsheet
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

import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.typesafe.config.Config;
import jakarta.inject.Inject;
import play.Environment;

/**
 * Creates providers to be used by dependency injection based on types in config values.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class ConfigTypedProvider {
    /**
     * Creates a provider based on a config value.
     *
     * @param configKey the config value holding the type to create
     * @param <T> type that the provider will provide
     * @return a new provider
     */
    public static <T> Provider<T> provider(final String configKey) {
        return new Provider<T>() {
            @Override
            public T get() {
                return _injector.getInstance(ConfigurationHelper.<T>getType(_environment, _configuration, configKey));
            }

            @Inject
            private Injector _injector;

            @Inject
            private Environment _environment;

            @Inject
            private Config _configuration;
        };
    }

    private ConfigTypedProvider() { }
}
