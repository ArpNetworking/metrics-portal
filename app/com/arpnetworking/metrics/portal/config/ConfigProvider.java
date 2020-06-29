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

import java.io.InputStream;
import java.util.function.Consumer;

/**
 * A {@code ConfigProvider} allows for periodically fetching and updating live configuration.
 *
 * @see StaticFileConfigProvider
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public interface ConfigProvider {
    /**
     * Start this config provider with the given {@code update} function.
     * <p>
     * The {@code InputStream} passed to the update callback is not required to
     * be buffered.
     *
     * The frequency and semantics of any updates are left up to the particular implementation.
     *
     * @param update The callback to invoke with the new config.
     */
    void start(Consumer<InputStream> update);

    /**
     * Stop this config provider.
     * <p>
     * Any future configuration updates will be ignored until {@link ConfigProvider#start} has been called again.
     */
    void stop();
}
