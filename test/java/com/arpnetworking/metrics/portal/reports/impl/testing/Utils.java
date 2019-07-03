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

package com.arpnetworking.metrics.portal.reports.impl.testing;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.File;
import java.io.IOException;

/**
 * Utilities for testing Chrome-based report renderers.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class Utils {

    /**
     * Path to the Chrome binary to use for Chrome-renderer tests.
     */
    public static final String CHROME_PATH = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";

    /**
     * Build a {@link Config} suitable for instantiation of a Chrome-based renderer.
     * @return a config.
     * @throws IOException if {@link #CHROME_PATH} doesn't point to an executable file on your system.
     */
    public static Config createChromeRendererConfig() throws IOException {
        if (!new File(CHROME_PATH).canExecute()) {
            throw new IOException("Utils#CHROME_PATH should point to an executable file; got " + CHROME_PATH);
        }

        return ConfigFactory.parseMap(ImmutableMap.of(
                "chromePath", CHROME_PATH,
                "chromeArgs", ImmutableMap.of(
                        "headless", true,
                        "no-sandbox", true
                )
        ));
    }

    private Utils() {}

}
