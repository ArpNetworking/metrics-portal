/*
 * Copyright 2020 Dropbox Inc.
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

package com.arpnetworking.kairos.config;

import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.junit.Test;

import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * Unit tests for {@link MetricsQueryConfigImpl}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class MetricsQueryConfigImplTest {
    private static final String QUERY_WHITELIST_CONFIG_KEY = "kairosdb.proxy.rollups.whitelist";
    private static final String POPULATE_WHITELIST_CONFIG_KEY = "rollup.metric.whitelist";
    private static final String POPULATE_BLACKLIST_CONFIG_KEY = "rollup.metric.blacklist";
    private static final Config INVALID_PERIODS = ConfigFactory.parseMap(ImmutableMap.of(
            QUERY_WHITELIST_CONFIG_KEY, ImmutableList.of(
                    ImmutableMap.of(
                            "pattern", "whitelisted_hourly_.*",
                            "periods", ImmutableList.of("bad value")
                    )
            ),
            POPULATE_WHITELIST_CONFIG_KEY, ImmutableList.of(".*"),
            POPULATE_BLACKLIST_CONFIG_KEY, ImmutableList.of()
    ));

    private static final Config INVALID_PATTERN = ConfigFactory.parseMap(ImmutableMap.of(
            QUERY_WHITELIST_CONFIG_KEY, ImmutableList.of(
                    ImmutableMap.of(
                            "pattern", "[",
                            "periods", ImmutableList.of("hours")
                    )
            ),
            POPULATE_WHITELIST_CONFIG_KEY, ImmutableList.of(".*"),
            POPULATE_BLACKLIST_CONFIG_KEY, ImmutableList.of()
    ));

    private static final Config VALID_CONFIG = ConfigFactory.parseMap(ImmutableMap.of(
            QUERY_WHITELIST_CONFIG_KEY, ImmutableList.of(
                    ImmutableMap.of(
                            "pattern", "whitelisted_hourly_.*",
                            "periods", ImmutableList.of("hours")
                    ),
                    ImmutableMap.of(
                            "pattern", "whitelisted_daily_.*",
                            "periods", ImmutableList.of("days")
                    ),
                    ImmutableMap.of(
                            "pattern", "whitelisted_all.*"
                    ),
                    ImmutableMap.of(
                            "pattern", "whitelisted_none.*",
                            "periods", ImmutableList.of()
                    )
            ),
            POPULATE_WHITELIST_CONFIG_KEY, ImmutableList.of(".*"),
            POPULATE_BLACKLIST_CONFIG_KEY, ImmutableList.of()
    ));

    private static final Set<SamplingUnit> ALL_SAMPLING_UNITS = ImmutableSet.copyOf(SamplingUnit.values());

    @Test
    public void testValidConfig() {
        new MetricsQueryConfigImpl(VALID_CONFIG);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPatternConfig() {
        new MetricsQueryConfigImpl(INVALID_PATTERN);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPeriodsConfig() {
        new MetricsQueryConfigImpl(INVALID_PERIODS);
    }

    @Test
    public void testRollupPeriodBlacklisting() {
        final MetricsQueryConfig queryConfig = new MetricsQueryConfigImpl(VALID_CONFIG);

        assertThat(queryConfig.getQueryEnabledRollups("whitelisted_hourly_foo"), contains(SamplingUnit.HOURS));
        assertThat(queryConfig.getQueryEnabledRollups("whitelisted_daily_foo"), contains(SamplingUnit.DAYS));
        assertThat(queryConfig.getQueryEnabledRollups("whitelisted_all_foo"), equalTo(ALL_SAMPLING_UNITS));
        assertThat(queryConfig.getQueryEnabledRollups("whitelisted_none_foo"), empty());
        assertThat(queryConfig.getQueryEnabledRollups("no_matches"), empty());
    }

    @Test
    public void testNoQueryForUnpopulatedRollups() {
        final MetricsQueryConfig config = new MetricsQueryConfigImpl(VALID_CONFIG);
        final MetricsQueryConfig configWithBlacklist = new MetricsQueryConfigImpl(
                VALID_CONFIG.withValue(POPULATE_BLACKLIST_CONFIG_KEY, ConfigValueFactory.fromAnyRef(ImmutableList.of(".*")))
        );

        assertThat(config.getQueryEnabledRollups("whitelisted_hourly_foo"), not(empty()));
        assertThat(configWithBlacklist.getQueryEnabledRollups("whitelisted_hourly_foo"), empty());
    }
}
