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

import com.google.common.collect.ImmutableSet;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.time.Instant;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Message containing the last datapoint timestamp for a metric series.
 * If no datapoints exist in the queried timerange then the lastDataPointTime will be
 * empty.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public final class LastDataPointMessage extends FailableMessage {

    public String getMetricName() {
        return _metricName;
    }

    public ImmutableSet<String> getTags() {
        return _tags;
    }

    public Optional<Instant> getLastDataPointTime() {
        return Optional.ofNullable(_lastDataPointTime);
    }

    public RollupPeriod getPeriod() {
        return _period;
    }

    public int getMaxBackfillPeriods() {
        return _maxBackfillPeriods;
    }

    private LastDataPointMessage(final Builder builder) {
        super(builder);
        _metricName = builder._metricName;
        _tags = builder._tags;
        _lastDataPointTime = builder._lastDataPointTime;
        _period = builder._period;
        _maxBackfillPeriods = builder._maxBackfillPeriods;
    }

    private final String _metricName;
    private final RollupPeriod _period;
    private final ImmutableSet<String> _tags;
    private final Instant _lastDataPointTime;
    private final int _maxBackfillPeriods;
    // TODO(cbriones): Generate a new serialVersionUID
    private static final long serialVersionUID = 5745882770658263619L;


    /**
     * {@link LastDataPointMessage} builder static inner class.
     */
    public static final class Builder extends FailableMessage.Builder<Builder, LastDataPointMessage> {

        /**
         * Creates a Builder for a LastDataPointMessage.
         */
        public Builder() {
            super(LastDataPointMessage::new);
        }

        /**
         * Sets the {@code _metricName} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code _metricName} to set
         * @return a reference to this Builder
         */
        public Builder setMetricName(final String value) {
            _metricName = value;
            return this;
        }

        /**
         * Sets the {@code tags} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code tags} to set
         * @return a reference to this Builder
         */
        public Builder setTags(final ImmutableSet<String> value) {
            _tags = value;
            return this;
        }

        /**
         * Sets the {@code _lastDataPointTime} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code _lastDataPointTime} to set
         * @return a reference to this Builder
         */
        public Builder setLastDataPointTime(@Nullable final Instant value) {
            _lastDataPointTime = value;
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
         * Sets the {@code _maxBackfillPeriods} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code _maxBackfillPeriods} to set
         * @return a reference to this Builder
         */
        public Builder setMaxBackfillPeriods(final int value) {
            _maxBackfillPeriods = value;
            return this;
        }

        @Override
        protected void reset() {
            _metricName = null;
            _period = null;
            _tags = ImmutableSet.of();
            _lastDataPointTime = null;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @NotNull
        @NotEmpty
        private String _metricName;
        @NotNull
        private RollupPeriod _period;
        @NotNull
        private ImmutableSet<String> _tags = ImmutableSet.of();

        private Instant _lastDataPointTime;
        private int _maxBackfillPeriods;
    }
}
