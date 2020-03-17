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

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.google.common.collect.ImmutableList;
import net.sf.oval.constraint.NotNull;

import java.util.List;
import java.util.regex.Pattern;

/**
 * ConfigEntry represents a query blacklist entry.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class QueryBlacklistEntry {
    private final Pattern _pattern;
    private final List<SamplingUnit> _periods;

    private QueryBlacklistEntry(final Builder builder) {
        this._pattern = builder._pattern;
        this._periods = builder._periods;
    }

    public Pattern getPattern() {
        return _pattern;
    }

    public List<SamplingUnit> getPeriods() {
        return _periods;
    }

    public static class Builder extends OvalBuilder<QueryBlacklistEntry> {
        /**
         * Creates a new {@link QueryBlacklistEntry} builder.
         */
        public Builder() {
            super(QueryBlacklistEntry::new);
        }

        /**
         * Set the periods for this config entry.
         * @param periods - the periods
         * @return This instance of Builder
         */
        public Builder setPeriods(final List<SamplingUnit> periods) {
            this._periods = ImmutableList.copyOf(periods);
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

        private List<SamplingUnit> _periods = ImmutableList.of();

        @NotNull
        private Pattern _pattern = null;
    }
}
