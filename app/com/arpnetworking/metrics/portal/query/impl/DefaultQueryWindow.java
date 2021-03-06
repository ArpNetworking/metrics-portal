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
import com.arpnetworking.metrics.portal.query.QueryAlignment;
import com.arpnetworking.metrics.portal.query.QueryWindow;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.time.Duration;

/**
 * Default implementation for {@link QueryWindow}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class DefaultQueryWindow implements QueryWindow {
    private final Duration _period;
    private final QueryAlignment _alignment;

    private DefaultQueryWindow(final Builder builder) {
        _period = builder._period;
        _alignment = builder._alignment;
    }

    public Duration getLookbackPeriod() {
        return _period;
    }

    public QueryAlignment getAlignment() {
        return _alignment;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultQueryWindow that = (DefaultQueryWindow) o;
        return Objects.equal(_period, that._period)
                && _alignment == that._alignment;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(_period, _alignment);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("_period", _period)
                .add("_alignment", _alignment)
                .toString();
    }

    /**
     * A builder for instances of {@link DefaultQueryWindow}.
     */
    public static final class Builder extends OvalBuilder<DefaultQueryWindow> {

        private QueryAlignment _alignment;
        private Duration _period;

        /**
         * Construct a new default Builder.
         */
        public Builder() {
            super(DefaultQueryWindow::new);
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
