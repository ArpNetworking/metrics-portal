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

import com.google.common.collect.ImmutableMultimap;
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
 * <p>
 * This class is a duplicate of {@link LastDataPointMessage} in order to avoid a deserialization problem
 * that can occur if received by existing clusters.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class LastDataPointsMessage extends FailableMessage {

    public String getSourceMetricName() {
        return _sourceMetricName;
    }

    public String getRollupMetricName() {
        return _rollupMetricName;
    }

    public ImmutableMultimap<String, String> getTags() {
        return _tags;
    }

    public Optional<Instant> getSourceLastDataPointTime() {
        return Optional.ofNullable(_sourceLastDataPointTime);
    }

    public Optional<Instant> getRollupLastDataPointTime() {
        return Optional.ofNullable(_rollupLastDataPointTime);
    }

    public RollupPeriod getPeriod() {
        return _period;
    }

    private LastDataPointsMessage(final Builder builder) {
        super(builder);
        _sourceMetricName = builder._sourceMetricName;
        _rollupMetricName = builder._rollupMetricName;
        _tags = builder._tags;
        _sourceLastDataPointTime = builder._sourceLastDataPointTime;
        _rollupLastDataPointTime = builder._rollupLastDataPointTime;
        _period = builder._period;
    }

    private final String _sourceMetricName;
    private final String _rollupMetricName;
    private final RollupPeriod _period;
    private final ImmutableMultimap<String, String> _tags;
    private final Instant _sourceLastDataPointTime;
    private final Instant _rollupLastDataPointTime;
    private static final long serialVersionUID = 2800761302248621189L;


    /**
     * {@link LastDataPointsMessage} builder static inner class.
     */
    public static final class Builder extends FailableMessage.Builder<Builder, LastDataPointsMessage> {

        /**
         * Creates a Builder for a LastDataPointMessage.
         */
        public Builder() {
            super(LastDataPointsMessage::new);
        }

        /**
         * Sets the {@code _rollupMetricName} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code _rollupMetricName} to set
         * @return a reference to this Builder
         */
        public Builder setRollupMetricName(final String value) {
            _rollupMetricName = value;
            return this;
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
         * Sets the {@code tags} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code tags} to set
         * @return a reference to this Builder
         */
        public Builder setTags(final ImmutableMultimap<String, String> value) {
            _tags = value;
            return this;
        }

        /**
         * Sets the {@code _sourceLastDataPointTime} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code _sourceLastDataPointTime} to set
         * @return a reference to this Builder
         */
        public Builder setSourceLastDataPointTime(@Nullable final Instant value) {
            _sourceLastDataPointTime = value;
            return this;
        }

        /**
         * Sets the {@code _rollupLastDataPointTime} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code _rollupLastDataPointTime} to set
         * @return a reference to this Builder
         */
        public Builder setRollupLastDataPointTime(@Nullable final Instant value) {
            _rollupLastDataPointTime = value;
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
            _sourceMetricName = null;
             _rollupMetricName = null;
            _period = null;
            _tags = ImmutableMultimap.of();
            _sourceLastDataPointTime = null;
            _rollupLastDataPointTime = null;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @NotNull
        @NotEmpty
        private String _sourceMetricName;
        @NotNull
        @NotEmpty
        private String _rollupMetricName;
        @NotNull
        private RollupPeriod _period;
        @NotNull
        private ImmutableMultimap<String, String> _tags = ImmutableMultimap.of();

        private Instant _rollupLastDataPointTime;
        private Instant _sourceLastDataPointTime;
    }
}
