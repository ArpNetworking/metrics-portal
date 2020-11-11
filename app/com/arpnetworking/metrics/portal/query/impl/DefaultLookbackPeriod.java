/*
 * Copyright 2020 Dropbox, Inc.
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

package com.arpnetworking.metrics.portal.query.impl;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.metrics.portal.query.LookbackPeriod;
import com.arpnetworking.metrics.portal.query.QueryAlignment;

import java.time.Duration;

/**
 * Default implementation for {@link LookbackPeriod}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class DefaultLookbackPeriod implements LookbackPeriod {
    private final Duration _period;
    private final QueryAlignment _alignment;

    private DefaultLookbackPeriod(final Builder builder) {
        _period = builder._period;
        _alignment = builder._alignment;
    }

    public Duration getPeriod() {
        return _period;
    }

    public QueryAlignment getAlignment() {
        return _alignment;
    }

    /**
     * A builder for instances of {@link DefaultLookbackPeriod}.
     */
    public static final class Builder extends OvalBuilder<DefaultLookbackPeriod> {

        private QueryAlignment _alignment;
        private Duration _period;

        /**
         * Construct a new default Builder.
         */
        public Builder() {
            super(DefaultLookbackPeriod::new);
        }

        /**
         * Sets the query alignment.
         *
         * @param alignment the alignment.
         * @return This instance of {@code Builder} for chaining.
         */
        public Builder setAlignment(final QueryAlignment alignment) {
            _alignment = alignment;
            return this;
        }

        /**
         * Sets the period.
         *
         * @param period the period.
         * @return This instance of {@code Builder} for chaining.
         */
        public Builder setPeriod(final Duration period) {
            _period = period;
            return this;
        }
    }
}
