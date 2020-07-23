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

package com.arpnetworking.metrics.portal.alerts.impl;

import com.arpnetworking.metrics.portal.config.ConfigProvider;

import java.io.InputStream;
import java.util.function.Consumer;

/**
 * A config provider that never sends any updates.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class NullConfigProvider implements ConfigProvider {
    private NullConfigProvider() {}

    @Override
    public void start(final Consumer<InputStream> update) { }

    @Override
    public void stop() { }

    public static NullConfigProvider getInstance() {
        return new NullConfigProvider();
    }
}
