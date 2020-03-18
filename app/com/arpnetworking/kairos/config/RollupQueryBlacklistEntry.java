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

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.google.common.collect.ImmutableSet;
import net.sf.oval.constraint.NotNull;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * An entry in a {@link MetricsQueryConfigImpl}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
final class RollupQueryBlacklistEntry {
    private final Pattern _pattern;
    private final Set<SamplingUnit> _periods;

    private RollupQueryBlacklistEntry(final Builder builder) {
        this._pattern = builder._pattern;
        this._periods = builder._periods;
    }

    public Pattern getPattern() {
        return _pattern;
    }

    public Set<SamplingUnit> getPeriods() {
        return _periods;
    }

    /**
     * A builder for instances of {@link RollupQueryBlacklistEntry}.
     *
     * @author Christian Briones (cbriones at dropbox dot com)
     */
    public static class Builder extends OvalBuilder<RollupQueryBlacklistEntry> {
        /**
         * Creates a new {@link RollupQueryBlacklistEntry} builder.
         */
        Builder() {
            super(RollupQueryBlacklistEntry::new);
        }

        /**
         * Set the periods for this config entry.
         * @param periods - the periods
         * @return This instance of Builder
         */
        public Builder setPeriods(final Set<SamplingUnit> periods) {
            this._periods = ImmutableSet.copyOf(periods);
            return this;
        }

        /**
         * Set the pattern for this config entry.
         * @param pattern - the pattern
         * @return This instance of Builder
         */
        public Builder setPattern(final Pattern pattern) {
            this._pattern = pattern;
            return this;
        }

        private Set<SamplingUnit> _periods = ImmutableSet.of();

        @NotNull
        private Pattern _pattern = null;
    }
}
