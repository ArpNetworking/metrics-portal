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
 * A {@link MetricsQueryConfig} that supports rollup whitelistingo
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
        _rollupQueryWhitelist = configuration.getConfigList("kairosdb.proxy.rollups.whitelist")
                .stream()
                .map(MetricsQueryConfigImpl::buildWhitelistEntry)
                .collect(ImmutableList.toImmutableList());
    }

    @Override
    public Set<SamplingUnit> getQueryEnabledRollups(final String metricName) {
        return queryWhitelistEntry(metricName)
            .map(RollupQueryWhitelistEntry::getPeriods)
            .orElse(ImmutableSet.of());
    }

    private Optional<RollupQueryWhitelistEntry> queryWhitelistEntry(final String metricName) {
        return _rollupQueryWhitelist.stream()
                .filter(
                    e -> e.getPattern().matcher(metricName).matches()
                )
                .findFirst();
    }

    private final List<RollupQueryWhitelistEntry> _rollupQueryWhitelist;
    private static final Set<SamplingUnit> ALL_SAMPLING_UNITS = ImmutableSet.copyOf(SamplingUnit.values());

    private static RollupQueryWhitelistEntry buildWhitelistEntry(final Config config) {
        final Set<SamplingUnit> periods;
        if (config.hasPath("periods")) {
            periods = config.getStringList("periods")
                    .stream()
                    .map(name -> SamplingUnit.valueOf(name.toUpperCase(Locale.ENGLISH)))
                    .collect(ImmutableSet.toImmutableSet());
        } else {
            periods = ALL_SAMPLING_UNITS;
        }

        return new RollupQueryWhitelistEntry.Builder()
                .setPattern(Pattern.compile(config.getString("pattern")))
                .setPeriods(periods)
                .build();
    }
}
