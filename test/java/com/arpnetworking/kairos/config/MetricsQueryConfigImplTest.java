package com.arpnetworking.kairos.config;

import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;

import java.util.Set;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class MetricsQueryConfigImplTest {

    private static final Config INVALID_PERIODS = ConfigFactory.parseMap(ImmutableMap.of(
            "kairosdb.proxy.rollups.blacklist", ImmutableList.of(
                    ImmutableMap.of(
                            "pattern", "blacklisted_hourly_.*",
                            "periods", ImmutableList.of("bad value")
                    )
            )
    ));

    private static final Config INVALID_PATTERN = ConfigFactory.parseMap(ImmutableMap.of(
            "kairosdb.proxy.rollups.blacklist", ImmutableList.of(
                    ImmutableMap.of(
                            "pattern", "[",
                            "periods", ImmutableList.of("hours")
                    )
            )
    ));

    private static final Config VALID_CONFIG = ConfigFactory.parseMap(ImmutableMap.of(
            "kairosdb.proxy.rollups.blacklist", ImmutableList.of(
                    ImmutableMap.of(
                            "pattern", "blacklisted_hourly_.*",
                            "periods", ImmutableList.of("hours")
                    ),
                    ImmutableMap.of(
                            "pattern", "blacklisted_daily_.*",
                            "periods", ImmutableList.of("days")
                    ),
                    ImmutableMap.of(
                            "pattern", "blacklisted_all_.*",
                            "periods", ImmutableList.of("hours", "days")
                    )
            )
    ));

    private static final Set<SamplingUnit> ALL_SAMPLING_UNITS = ImmutableSet.copyOf(SamplingUnit.values());

    @Test
    public void testValidConfig() {
        new MetricsQueryConfigImpl(VALID_CONFIG);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvalidPatternConfig() {
        new MetricsQueryConfigImpl(INVALID_PATTERN);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvalidPeriodsConfig() {
        new MetricsQueryConfigImpl(INVALID_PERIODS);
    }

    @Test
    public void testRollupPeriodBlacklisting() {
        final MetricsQueryConfig queryConfig = new MetricsQueryConfigImpl(VALID_CONFIG);

        assertThat(queryConfig.getQueryEnabledRollups("blacklisted_hourly_foo"), not(contains(SamplingUnit.HOURS)));
        assertThat(queryConfig.getQueryEnabledRollups("blacklisted_daily_foo"), not(contains(SamplingUnit.DAYS)));
        assertThat(queryConfig.getQueryEnabledRollups("blacklisted_all_foo"), not(contains(SamplingUnit.HOURS)));
        assertThat(queryConfig.getQueryEnabledRollups("blacklisted_all_foo"), not(contains(SamplingUnit.DAYS)));
        assertThat(queryConfig.getQueryEnabledRollups("not_blacklisted"), equalTo(ALL_SAMPLING_UNITS));
    }
}