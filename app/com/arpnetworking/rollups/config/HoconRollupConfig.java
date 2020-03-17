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

package com.arpnetworking.rollups.config;

import com.arpnetworking.rollups.RollupPeriod;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.typesafe.config.Config;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class HoconRollupConfig implements RollupConfig {
    @Inject
    public HoconRollupConfig(final Config configuration) {
        _queryBlacklist = configuration.getConfigList("rollups.metric.query.blacklist")
                .stream()
                .map(HoconRollupConfig::buildConfigEntry)
                .collect(ImmutableList.toImmutableList());

        _generateWhitelist = toPredicate(configuration.getStringList("rollups.metric.whitelist"), true);
        _generateBlacklist = toPredicate(configuration.getStringList("rollups.metric.blacklist"), false);
    }

    @Override
    public boolean isGenerationEnabled(final String metricName) {
        return _generateWhitelist.test(metricName) && !_generateBlacklist.test(metricName);
    }

    @Override
    public boolean isQueryEnabled(final String metricName, final RollupPeriod period) {
        return queryBlacklistEntry(metricName)
                .map(ConfigEntry::getPeriods)
                .map(periods -> periods.contains(period))
                .orElse(true);
    }

    private Optional<ConfigEntry> queryBlacklistEntry(final String metricName) {
        return _queryBlacklist.stream()
                .filter(
                    e -> e.getPattern().matcher(metricName).matches()
                )
                .findFirst();
    }

    private final Predicate<String> _generateWhitelist;
    private final Predicate<String> _generateBlacklist;

    private final List<ConfigEntry> _queryBlacklist;

    static ConfigEntry buildConfigEntry(final Config config) {
        final List<RollupPeriod> periods =
            config.getStringList("periods")
                    .stream()
                    .map(name -> RollupPeriod.valueOf(name.toUpperCase(Locale.ENGLISH)))
                    .collect(ImmutableList.toImmutableList());

        return new ConfigEntry.Builder()
                .setPattern(Pattern.compile(config.getString("pattern")))
                .setPeriods(periods)
                .build();
    }

    static Predicate<String> toPredicate(final List<String> regexList, final boolean defaultResult) {
        return regexList
                .stream()
                .map(Pattern::compile)
                .map(Pattern::asPredicate)
                .reduce(Predicate::or)
                .orElse(t -> defaultResult);
    }
}
