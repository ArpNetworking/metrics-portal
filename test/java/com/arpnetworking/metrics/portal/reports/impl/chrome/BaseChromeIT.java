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

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import models.internal.TimeRange;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Base class for tests for Chrome-based renderers.
 *
 * This test-class requires that Chrome be installed on the system running it; else, it will be ignored.
 * It looks for Chrome in a few "standard" places (see {@link #POSSIBLE_CHROME_PATHS}), then gives up.
 * If it doesn't have the path for your system, add your system's path (and consider checking it in!).
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class BaseChromeIT {

    @Rule
    public WireMockRule _wireMock = new WireMockRule(wireMockConfig().dynamicPort());

    public static final TimeRange DEFAULT_TIME_RANGE = new TimeRange(Instant.EPOCH, Instant.EPOCH);
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    private static final ImmutableList<String> POSSIBLE_CHROME_PATHS = ImmutableList.of(
            "/usr/bin/chromium",
            "/usr/bin/chromium-browser",
            "/usr/bin/google-chrome-stable",
            "/usr/bin/google-chrome",
            "/usr/lib/chromium/chrome",
            "/Applications/Chromium.app/Contents/MacOS/Chromium",
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/Applications/Google Chrome Canary.app/Contents/MacOS/Google Chrome Canary"
    );

    /**
     * Path to the Chrome binary to use for Chrome-renderer tests.
     */
    private static final Optional<String> CHROME_PATH = POSSIBLE_CHROME_PATHS.stream()
            .filter(BaseChromeIT::isPathExecutable)
            .findFirst();

    /**
     * Config to use to instantiate Chrome-based renderers.
     */
    protected static final Config CHROME_RENDERER_CONFIG = ConfigFactory.parseMap(ImmutableMap.of(
            "chromePath", CHROME_PATH.orElse("<could not find Chrome>"),
            "chromeArgs", ImmutableMap.of(
                    "headless", true,
                    "no-sandbox", true
            )
    ));

    private static boolean isPathExecutable(final String path) {
        return Files.isExecutable(FileSystems.getDefault().getPath(path));
    }

    @BeforeClass
    public static void setUpClass() {
        Assume.assumeTrue("could not find Chrome in any likely location", CHROME_PATH.isPresent());
    }
}
