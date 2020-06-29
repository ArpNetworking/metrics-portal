/*
 * Copyright 2020 Dropbox, Inc.
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

package com.arpnetworking.metrics.portal.config;

import com.arpnetworking.metrics.portal.config.impl.StaticFileConfigProvider;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.typesafe.config.Config;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.nio.file.Paths;

/**
 * Bindings for choosing a {@link ConfigProvider} instance from {@link Config}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class ConfigProviderModule extends AbstractModule {
    @Override
    protected void configure() {}

    @Provides
    @SuppressFBWarnings(
            value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "Reflectively invoked by Guice"
    )
    private StaticFileConfigProvider provideStaticFileConfigLoader(
            final Config config
    ) {
        final String path = config.getString("path");
        return new StaticFileConfigProvider(
                Paths.get(path)
        );
    }
}
