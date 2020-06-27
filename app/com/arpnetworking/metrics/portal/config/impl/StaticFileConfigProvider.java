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

package com.arpnetworking.metrics.portal.config.impl;

import com.arpnetworking.metrics.portal.config.ConfigProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;

/**
 * A config provider that provides static content from a file.
 * <p>
 * The file is only ever loaded once, any subsequent changes will be ignored.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class StaticFileConfigProvider implements ConfigProvider {
    private final Path _path;
    private boolean _started = false;

    /**
     * Create a provider for the file referenced by {@code path}.
     * <p>
     * The input stream returned is not buffered.
     *
     * @param path The path of the file to read.
     */
    public StaticFileConfigProvider(
            final Path path
    ) {
        _path = path;
    }

    @Override
    public void start(final Consumer<InputStream> update) {
        assertStarted(false);
        _started = true;

        try (InputStream stream = Files.newInputStream(_path, StandardOpenOption.READ)) {
            update.accept(stream);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        assertStarted(true);
        _started = false;
    }

    private void assertStarted(final boolean state) {
        if (state != _started) {
            throw new IllegalStateException("Provider is not " + (state ? "started" : "stopped"));
        }
    }
}
