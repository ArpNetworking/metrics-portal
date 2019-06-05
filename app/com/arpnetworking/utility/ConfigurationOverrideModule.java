/*
 * Copyright 2019 Dropbox, Inc
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

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.Assisted;
import com.typesafe.config.Config;

/**
 * Guice module that provides a way to bind a scoped/subset config to allow more dynamic config loading.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
 */
public final class ConfigurationOverrideModule extends AbstractModule {
    /**
     * Public constructor.
     *
     * @param config configuration to use in the nested bindings
     */
    public ConfigurationOverrideModule(final Config config) {
        _config = config;
    }

    @Override
    protected void configure() {
        bind(Config.class).annotatedWith(Assisted.class).toInstance(_config);
    }

    private final Config _config;
}
