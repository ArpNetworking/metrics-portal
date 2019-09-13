/*
 * Copyright 2019 Dropbox, Inc.
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
package com.arpnetworking.metrics.portal.reports.impl.chrome;

import com.github.kklisura.cdt.launch.ChromeArguments;
import com.github.kklisura.cdt.launch.ChromeLauncher;
import com.github.kklisura.cdt.launch.config.ChromeLauncherConfiguration;
import com.github.kklisura.cdt.launch.support.impl.ProcessLauncherImpl;
import com.github.kklisura.cdt.services.ChromeService;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.Objects;

/**
 * Factory that creates Chrome instances, caching them to avoid creating redundant processes. Thread-safe.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class CachingChromeServiceFactory {
    /**
     * Get a Chrome instance with the given parameters, creating it if it doesn't already exist.
     *
     * @param path Path to the Chrome executable.
     * @param args Command-line options to pass to that Chrome instance.
     * @return The (possibly freshly-created, possibly pre-existing) Chrome instance.
     */
    public ChromeService getOrCreate(final String path, final ImmutableMap<String, Object> args) {
        return _cache.computeIfAbsent(new ChromeServiceKey(path, args), this::create);
    }

    private ChromeService create(final ChromeServiceKey key) {
        // The config should be able to override the CHROME_PATH environment variable that ChromeLauncher uses.
        // This requires in our own custom "environment" (since it defaults to using System::getEnv).
        final ImmutableMap<String, String> env = ImmutableMap.of(
                ChromeLauncher.ENV_CHROME_PATH, key._path
        );
        // ^^^ In order to pass this environment in, we need to use a many-argument constructor,
        //   which doesn't have obvious default values. So I stole the arguments from the fewer-argument constructor:
        // CHECKSTYLE.OFF: LineLength
        //   https://github.com/kklisura/chrome-devtools-java-client/blob/master/cdt-java-client/src/main/java/com/github/kklisura/cdt/launch/ChromeLauncher.java#L105
        // CHECKSTYLE.ON: LineLength
        final ChromeLauncher launcher = new ChromeLauncher(
                new ProcessLauncherImpl(),
                env::get,
                new ChromeLauncher.RuntimeShutdownHookRegistry(),
                new ChromeLauncherConfiguration()
        );
        return launcher.launch(ChromeArguments.defaults(true)
                .additionalArguments(key._args)
                .build());
    }

    private final Map<ChromeServiceKey, ChromeService> _cache = Maps.newConcurrentMap();

    private static final class ChromeServiceKey {
        private final String _path;
        private final ImmutableMap<String, Object> _args;
        private ChromeServiceKey(final String path, final ImmutableMap<String, Object> args) {
            _path = path;
            _args = args;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ChromeServiceKey that = (ChromeServiceKey) o;
            return _path.equals(that._path)
                    && _args.equals(that._args);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_path, _args);
        }
    }
}
