/*
 * Copyright 2019 Dropbox
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
import com.arpnetworking.logback.annotations.Loggable;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.sf.oval.constraint.NotNull;
import net.sf.oval.constraint.ValidateWithMethod;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Model class to represent a tags query.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
@Loggable
public final class TagsQuery {
    /**
     * Gets the relative start time of the query (inclusive). Only one of
     * {@link #getStartTimeRelative()} and {@link #getStartTimeMillis()} is set to a
     * non-empty value.
     *
     * @return the start time in milliseconds
     */
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    @JsonProperty("start_relative")
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    private Optional<RelativeDateTime> getStartTimeRelative() {
        return _startTimeRelative;
    }

    /**
     * Gets the start time of the query in epoch milliseconds (inclusive).
     *
     * @return the start time in milliseconds
     */
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    @JsonProperty("start_absolute")
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    private Optional<Long> getStartTimeMillis() {
        return _startTime.map(Instant::toEpochMilli);
    }

    /**
     * Gets the end time of the query in epoch milliseconds (inclusive).
     *
     * @return the end time in milliseconds
     */
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    @JsonProperty("end_absolute")
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    private Optional<Long> getEndTimeMillis() {
        return _endTime.map(Instant::toEpochMilli);
    }

    /**
     * Gets the relative end time of the query (inclusive). Only up to
     * one of {@link #getEndTimeRelative()} and {@link #getEndTimeMillis()}
     * is set to a non-empty value. If both return an empty value then the
     * end date is assumed to be the current date and time.
     *
     * @return the start time in milliseconds
     */
    @SuppressFBWarnings("UPM_UNCALLED_PRIVATE_METHOD")
    @JsonProperty("end_relative")
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    private Optional<RelativeDateTime> getEndTimeRelative() {
        return _endTimeRelative;
    }

    @JsonProperty(value = "metrics")
    public ImmutableList<MetricTags> getMetrics() {
        return _metrics;
    }

    @JsonIgnore
    public Optional<Instant> getStartTime() {
        return _startTime;
    }

    @JsonIgnore
    public Optional<Instant> getEndTime() {
        return _endTime;
    }

    @JsonAnyGetter
    public ImmutableMap<String, Object> getOtherArgs() {
        return _otherArgs;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("startTime", _startTime)
                .add("endTime", _endTime)
                .add("startTimeRelative", _startTimeRelative)
                .add("endTimeRelative", _endTimeRelative)
                .add("metrics", _metrics)
                .add("otherArgs", _otherArgs)
                .toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TagsQuery that = (TagsQuery) o;
        return Objects.equals(_startTime, that._startTime)
                && Objects.equals(_endTime, that._endTime)
                && Objects.equals(_startTimeRelative, that._startTimeRelative)
                && Objects.equals(_endTimeRelative, that._endTimeRelative)
                && Objects.equals(_metrics, that._metrics)
                && Objects.equals(_otherArgs, that._otherArgs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_startTime, _endTime, _startTimeRelative, _endTimeRelative, _metrics, _otherArgs);
    }

    private TagsQuery(final Builder builder) {
        _startTime = Optional.ofNullable(builder._startTime);
        _endTime = Optional.ofNullable(builder._endTime);
        _startTimeRelative = Optional.ofNullable(builder._startRelative);
        _endTimeRelative = Optional.ofNullable(builder._endRelative);
        _metrics = builder._metrics;
        _otherArgs = ImmutableMap.copyOf(builder._otherArgs);
    }

    private final Optional<Instant> _startTime;
    private final Optional<Instant> _endTime;
    private final Optional<RelativeDateTime> _startTimeRelative;
    private final Optional<RelativeDateTime> _endTimeRelative;
    private final ImmutableList<MetricTags> _metrics;
    private final ImmutableMap<String, Object> _otherArgs;

    /**
     * Implementation of the builder pattern for {@link TagsQuery}.
     */
    public static final class Builder extends ThreadLocalBuilder<TagsQuery> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(TagsQuery::new);
        }

        /**
         * Sets the start time of the query. This is a convenience method
         * for {@link #setStartTimeMillis(Long)}. Start time must be set
         * with one of these:
         *
         * <ul>
         *     <li>{@link #setStartTime(Instant)}</li>
         *     <li>{@link #setStartTimeMillis(Long)}</li>
         *     <li>{@link #setStartTimeRelative(RelativeDateTime)}</li>
         * </ul>
         *
         * The first two set an absolute start time while the third sets
         * a relative start time.
         *
         * @param value the start time
         * @return this {@link Builder}
         */
        public Builder setStartTime(@Nullable final Instant value) {
            _startTime = value;
            return this;
        }

        /**
         * Sets the absolute start time in milliseconds. Start time must be set
         * with one of these:
         *
         * <ul>
         *     <li>{@link #setStartTime(Instant)}</li>
         *     <li>{@link #setStartTimeMillis(Long)}</li>
         *     <li>{@link #setStartTimeRelative(RelativeDateTime)}</li>
         * </ul>
         *
         * The first two set an absolute start time while the third sets
         * a relative start time.
         *
         * @param millis the start time in milliseconds
         * @return this {@link Builder}
         */
        @JsonProperty("start_absolute")
        public Builder setStartTimeMillis(@Nullable final Long millis) {
            _startTime = millis == null ? null : Instant.ofEpochMilli(millis);
            return this;
        }

        /**
         * Sets the relative start time. Start time must be set
         * with one of these:
         *
         * <ul>
         *     <li>{@link #setStartTime(Instant)}</li>
         *     <li>{@link #setStartTimeMillis(Long)}</li>
         *     <li>{@link #setStartTimeRelative(RelativeDateTime)}</li>
         * </ul>
         *
         * The first two set an absolute start time while the third sets
         * a relative start time.
         *
         * @param value the relative start time
         * @return this {@link Builder}
         */
        @JsonProperty("start_relative")
        public Builder setStartTimeRelative(@Nullable final RelativeDateTime value) {
            _startRelative = value;
            return this;
        }

        /**
         * Sets the end time of the query. Null is used as "now" from KairosDB.  Optional. Default is null.
         *
         * @param value the end time
         * @return this {@link Builder}
         */
        public Builder setEndTime(@Nullable final Instant value) {
            _endTime = value;
            return this;
        }

        /**
         * Sets the absolute end time in milliseconds.
         *
         * @param millis the end time in milliseconds
         * @return this {@link Builder}
         */
        @JsonProperty("end_absolute")
        public Builder setEndTimeMillis(@Nullable final Long millis) {
            _endTime = millis == null ? null : Instant.ofEpochMilli(millis);
            return this;
        }

        /**
         * Sets the relative end time. End time is optional and effectively defaults
         * to the current date and time. Setting end time can only be done with up to
         * one of these:
         *
         * <ul>
         *     <li>{@link #setEndTime(Instant)}</li>
         *     <li>{@link #setEndTimeMillis(Long)}</li>
         *     <li>{@link #setEndTimeRelative(RelativeDateTime)}</li>
         * </ul>
         *
         * The first two set an absolute end time while the third sets
         * a relative end time.
         *
         * @param value the relative end time
         * @return this {@link Builder}
         */
        @JsonProperty("end_relative")
        public Builder setEndTimeRelative(@Nullable final RelativeDateTime value) {
            _endRelative = value;
            return this;
        }

        /**
         * Sets list of metrics and tags to query. Required. Cannot be null.
         *
         * @param value the metric tags
         * @return this {@link Builder}
         */
        public Builder setMetrics(final ImmutableList<MetricTags> value) {
            _metrics = value;
            return this;
        }

        /**
         * Adds an attribute not explicitly modeled by this class. Optional.
         *
         * @param key the attribute name
         * @param value the attribute value
         * @return this {@link Builder}
         */
        @JsonAnySetter
        public Builder addOtherArg(final String key, final Object value) {
            _otherArgs.put(key, value);
            return this;
        }

        /**
         * Sets the attributes not explicitly modeled by this class. Optional.
         *
         * @param value the other attributes
         * @return this {@link Builder}
         */
        @JsonIgnore
        public Builder setOtherArgs(final ImmutableMap<String, Object> value) {
            _otherArgs = value;
            return this;
        }

        @Override
        protected void reset() {
            _startTime = null;
            _endTime = null;
            _startRelative = null;
            _endRelative = null;
            _otherArgs = Maps.newHashMap();
        }

        private boolean validateStart(@Nullable final Instant ignored) {
            if (_startTime == null) {
                return _startRelative != null;
            } else {
                return _startRelative == null;
            }
        }

        private boolean validateEnd(@Nullable final Instant ignored) {
            if (_endTime != null) {
                return _endRelative == null;
            }
            return true;
        }

        @ValidateWithMethod(methodName = "validateStart", parameterType = Instant.class)
        private Instant _startTime;
        @ValidateWithMethod(methodName = "validateEnd", parameterType = Instant.class)
        private Instant _endTime;
        private RelativeDateTime _startRelative;
        private RelativeDateTime _endRelative;
        @NotNull
        private ImmutableList<MetricTags> _metrics = ImmutableList.of();
        @NotNull
        private Map<String, Object> _otherArgs = Maps.newHashMap();
    }
}
