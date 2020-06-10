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
package com.arpnetworking.rollups;

import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.google.common.base.MoreObjects;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Identifies a rolled-up KairosDB metric.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class RollupMetric {
    private final String _baseMetricName;
    private final RollupPeriod _period;

    public String getBaseMetricName() {
        return _baseMetricName;
    }

    /**
     * Return the name that the rolled-up data should be stored under in KairosDB, e.g. "my_metric_1h".
     *
     * @return the metric name for the rolled-up data
     */
    public String getRollupMetricName() {
        return _baseMetricName + _period.getSuffix();
    }

    public RollupPeriod getPeriod() {
        return _period;
    }

    /**
     * Inverse of {@link #getRollupMetricName()}: parse a KairosDB metric name.
     *
     * @param name the KairosDB metric name
     * @return the parsed {@link RollupMetric} (if the string can be parsed)
     */
    public static Optional<RollupMetric> fromRollupMetricName(final String name) {
        for (final RollupPeriod period : RollupPeriod.values()) {
            final String suffix = period.getSuffix();
            if (name.endsWith(suffix)) {
                final String basename = name.substring(0, name.length() - suffix.length());
                if (!basename.isEmpty()) {
                    return Optional.of(ThreadLocalBuilder.build(Builder.class, b -> b
                            .setBaseMetricName(basename)
                            .setPeriod(period)
                    ));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RollupMetric that = (RollupMetric) o;
        return _baseMetricName.equals(that._baseMetricName)
                && _period == that._period;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_baseMetricName, _period);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("_baseMetricName", _baseMetricName)
                .add("_period", _period)
                .toString();
    }

    private RollupMetric(final Builder builder) {
        _baseMetricName = builder._baseMetricName;
        _period = builder._period;
    }


    /**
     * {@link RollupMetric} builder static inner class.
     */
    public static final class Builder extends ThreadLocalBuilder<RollupMetric> {

        /**
         * Creates a builder.
         */
        public Builder() {
            super(RollupMetric::new);
        }

        /**
         * Sets the {@code _baseMetricName} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code _baseMetricName} to set
         * @return a reference to this Builder
         */
        public Builder setBaseMetricName(final String value) {
            _baseMetricName = value;
            return this;
        }

        /**
         * Sets the {@code _period} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code _period} to set
         * @return a reference to this Builder
         */
        public Builder setPeriod(final RollupPeriod value) {
            _period = value;
            return this;
        }


        @Override
        protected void reset() {
            _baseMetricName = null;
            _period = null;
        }

        @NotNull
        @NotEmpty
        private String _baseMetricName;
        @NotNull
        private RollupPeriod _period;
    }
}
