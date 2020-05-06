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

import com.arpnetworking.logback.annotations.Loggable;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

/**
 * Message class for signifying the end of a rollup workflow.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
@Loggable
public final class FinishRollupMessage extends FailableMessage {

    public String getMetricName() {
        return _metricName;
    }

    public RollupPeriod getPeriod() {
        return _period;
    }

    private final String _metricName;
    private final RollupPeriod _period;
    private static final long serialVersionUID = -7098548779115714541L;

    private FinishRollupMessage(final Builder builder) {
        super(builder);
        _metricName = builder._metricName;
        _period = builder._period;
    }

    /**
     * {@link FinishRollupMessage} builder static inner class.
     */
    public static final class Builder extends FailableMessage.Builder<Builder, FinishRollupMessage> {

        /**
         * Creates a builder for a FinishRollupMessage.
         */
        public Builder() {
            super(FinishRollupMessage::new);
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
            super.reset();
            _metricName = null;
            _period = null;
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
    }
}
