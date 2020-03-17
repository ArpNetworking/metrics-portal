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

import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.typesafe.config.Config;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class HoconRollupConfig implements QueryRollupBlacklist {
    @Inject
    public HoconRollupConfig(final Config configuration) {
        _queryBlacklist = configuration.getConfigList("rollups.metric.query.blacklist")
                .stream()
                .map(HoconRollupConfig::buildBlacklistEntry)
                .collect(ImmutableList.toImmutableList());
    }

    @Override
    public boolean isRollupUseEnabled(final String metricName, final SamplingUnit samplingUnit) {
        return queryBlacklistEntry(metricName)
                .map(QueryBlacklistEntry::getPeriods)
                .map(periods -> periods.contains(samplingUnit))
                .orElse(true);
    }

    private Optional<QueryBlacklistEntry> queryBlacklistEntry(final String metricName) {
        return _queryBlacklist.stream()
                .filter(
                    e -> e.getPattern().matcher(metricName).matches()
                )
                .findFirst();
    }

    private final List<QueryBlacklistEntry> _queryBlacklist;

    static QueryBlacklistEntry buildBlacklistEntry(final Config config) {
        final List<SamplingUnit> periods =
            config.getStringList("periods")
                    .stream()
                    .map(name -> SamplingUnit.valueOf(name.toUpperCase(Locale.ENGLISH)))
                    .collect(ImmutableList.toImmutableList());

        return new QueryBlacklistEntry.Builder()
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
