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
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.typesafe.config.Config;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A {@link MetricsQueryConfig} that supports rollup blacklisting.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class MetricsQueryConfigImpl implements MetricsQueryConfig {

    /**
     * Construct a new {@code MetricsQueryImpl} by parsing a {@link Config}.
     *
     * @param configuration Play configuration to load from
     */
    @Inject
    public MetricsQueryConfigImpl(final Config configuration) {
        _rollupQueryBlacklist = configuration.getConfigList("kairosdb.proxy.rollups.blacklist")
                .stream()
                .map(MetricsQueryConfigImpl::buildBlacklistEntry)
                .collect(ImmutableList.toImmutableList());
    }

    @Override
    public Set<SamplingUnit> getQueryEnabledRollups(final String metricName) {
        final Set<SamplingUnit> disabledUnits = queryBlacklistEntry(metricName)
                .map(RollupQueryBlacklistEntry::getPeriods)
                .orElse(ImmutableSet.of());
        if (disabledUnits.isEmpty()) {
            return ALL_SAMPLING_UNITS;
        }
        return ALL_SAMPLING_UNITS
                .stream()
                .filter(unit -> !disabledUnits.contains(unit))
                .collect(ImmutableSet.toImmutableSet());
    }

    private Optional<RollupQueryBlacklistEntry> queryBlacklistEntry(final String metricName) {
        return _rollupQueryBlacklist.stream()
                .filter(
                    e -> e.getPattern().matcher(metricName).matches()
                )
                .findFirst();
    }

    private final List<RollupQueryBlacklistEntry> _rollupQueryBlacklist;
    private static final Set<SamplingUnit> ALL_SAMPLING_UNITS = ImmutableSet.copyOf(SamplingUnit.values());

    private static RollupQueryBlacklistEntry buildBlacklistEntry(final Config config) {
        final Set<SamplingUnit> periods =
            config.getStringList("periods")
                    .stream()
                    .map(name -> SamplingUnit.valueOf(name.toUpperCase(Locale.ENGLISH)))
                    .collect(ImmutableSet.toImmutableSet());

        return new RollupQueryBlacklistEntry.Builder()
                .setPattern(Pattern.compile(config.getString("pattern")))
                .setPeriods(periods)
                .build();
    }
}
