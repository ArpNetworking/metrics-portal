/*
 * Copyright 2017 Smartsheet
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
package com.arpnetworking.kairos.client.models;

import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import models.internal.TimeSeriesResult;
import models.internal.impl.DefaultTimeSeriesResult;
import net.sf.oval.constraint.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Model class for a data point in a kairosdb metrics query.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class DataPoint {
    public Instant getTime() {
        return _time;
    }

    public Object getValue() {
        return _value;
    }

    /**
     * Converts this to an internal model.
     *
     * @return a new internal model
     */
    public TimeSeriesResult.DataPoint toInternal() {
        return new DefaultTimeSeriesResult.DataPoint.Builder()
                .setTime(_time)
                .setValue(_value)
                .build();
    }

    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD") // Invoked reflectively by Jackson
    @JsonValue
    private ImmutableList<Object> serialize() {
        return ImmutableList.of(_time.toEpochMilli(), _value);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataPoint otherDataPoint = (DataPoint) o;
        return Objects.equals(_time, otherDataPoint._time)
                && Objects.equals(_value, otherDataPoint._value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_time, _value);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("time", _time)
                .add("value", _value)
                .toString();
    }

    private DataPoint(final Builder builder) {
        _time = builder._time;
        _value = builder._value;
    }

    private final Instant _time;
    private final Object _value;

    /**
     * Implementation of the builder pattern for a {@link DataPoint}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends ThreadLocalBuilder<DataPoint> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(DataPoint::new);
        }

        @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
        @JsonCreator
        private static Builder createFromJsonArray(final List<Object> jsonArray) {
            return new Builder()
                    .setTime(Instant.ofEpochMilli(((Number) jsonArray.get(0)).longValue()))
                    .setValue(jsonArray.get(1));
        }

        /**
         * Sets the time. Required. Cannot be null.
         *
         * @param value the time
         * @return this {@link Builder}
         */
        public Builder setTime(final Instant value) {
            _time = value;
            return this;
        }

        /**
         * Sets the value. Required. Cannot be null.
         *
         * @param value the value
         * @return this {@link Builder}
         */
        public Builder setValue(final Object value) {
            _value = value;
            return this;
        }

        @Override
        protected void reset() {
            _time = null;
            _value = null;
        }

        @NotNull
        private Instant _time;
        @NotNull
        private Object _value;
    }
}
