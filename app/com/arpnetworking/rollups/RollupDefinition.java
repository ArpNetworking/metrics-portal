/*
 * Copyright 2019 Dropbox Inc.
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

import akka.routing.ConsistentHashingRouter;
import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Data class that holds the information necessary to rollup a metric for a single period.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
@Loggable
public final class RollupDefinition implements Serializable, ConsistentHashingRouter.ConsistentHashable {
    private static final long serialVersionUID = -1098548879115718541L;
    private final String _sourceMetricName;
    private final String _destinationMetricName;
    private final RollupPeriod _period;
    private final Instant _startTime;
    private final ImmutableMap<String, String> _filterTags;
    private final ImmutableMultimap<String, String> _allMetricTags;

    private RollupDefinition(final Builder builder) {
        _sourceMetricName = builder._sourceMetricName;
        _destinationMetricName = builder._destinationMetricName;
        _period = builder._period;
        _startTime = builder._startTime;
        _filterTags = builder._filterTags;
        _allMetricTags = builder._allMetricTags;
    }

    public String getSourceMetricName() {
        return _sourceMetricName;
    }

    public String getDestinationMetricName() {
        return _destinationMetricName;
    }

    public RollupPeriod getPeriod() {
        return _period;
    }

    public Instant getStartTime() {
        return _startTime;
    }

    public ImmutableMap<String, String> getFilterTags() {
        return _filterTags;
    }

    public ImmutableMultimap<String, String> getAllMetricTags() {
        return _allMetricTags;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RollupDefinition that = (RollupDefinition) o;
        return _sourceMetricName.equals(that._sourceMetricName)
                && _destinationMetricName.equals(that._destinationMetricName)
                && _period == that._period
                && _startTime.equals(that._startTime)
                && _filterTags.equals(that._filterTags)
                && _allMetricTags.equals(that._allMetricTags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_sourceMetricName, _destinationMetricName, _period, _startTime, _filterTags, _allMetricTags);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("_sourceMetricName", _sourceMetricName)
                .add("_destinationMetricName", _destinationMetricName)
                .add("_period", _period)
                .add("_startTime", _startTime)
                .add("_filterTags", _filterTags)
                .add("_allMetricTags", _allMetricTags)
                .toString();
    }

    @Override
    public Object consistentHashKey() {
        return hashCode();
    }

    public Instant getEndTime() {
        return _startTime.plus(_period.periodCountToDuration(1)).minusMillis(1);
    }

    /**
     * {@code RollupDefinition} builder static inner class.
     */
    public static final class Builder extends OvalBuilder<RollupDefinition> {
        @NotNull
        @NotEmpty
        private String _sourceMetricName;
        @NotNull
        @NotEmpty
        private String _destinationMetricName;
        @NotNull
        private RollupPeriod _period;
        @NotNull
        private Instant _startTime;
        @NotNull
        private ImmutableMap<String, String> _filterTags = ImmutableMap.of();
        @NotNull
        private ImmutableMultimap<String, String> _allMetricTags;

        /**
         * Creates a builder for a RollupDefinition.
         */
        public Builder() {
            super(RollupDefinition::new);
        }

        /**
         * Sets the {@code _sourceMetricName} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code _sourceMetricName} to set
         * @return a reference to this Builder
         */
        public Builder setSourceMetricName(final String value) {
            _sourceMetricName = value;
            return this;
        }

        /**
         * Sets the {@code _destinationMetricName} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code _destinationMetricName} to set
         * @return a reference to this Builder
         */
        public Builder setDestinationMetricName(final String value) {
            _destinationMetricName = value;
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

        /**
         * Sets the {@code _startTime} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code _startTime} to set
         * @return a reference to this Builder
         */
        public Builder setStartTime(final Instant value) {
            _startTime = value;
            return this;
        }

        /**
         * Sets the {@code _filterTags} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code _filterTags} to set
         * @return a reference to this Builder
         */
        public Builder setFilterTags(final ImmutableMap<String, String> value) {
            _filterTags = value;
            return this;
        }

        /**
         * Sets the {@code _allMetricTags} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code _allMetricTags} to set
         * @return a reference to this Builder
         */
        public Builder setAllMetricTags(final ImmutableMultimap<String, String> value) {
            _allMetricTags = value;
            return this;
        }
    }
}
