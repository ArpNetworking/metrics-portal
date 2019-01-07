/*
 * Copyright 2018 Dropbox, Inc.
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
package com.arpnetworking.metrics.portal.scheduling.impl;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import models.internal.scheduling.BaseSchedule;
import models.internal.scheduling.Schedule;
import net.sf.oval.ConstraintViolation;
import net.sf.oval.constraint.Assert;
import net.sf.oval.constraint.MaxCheck;
import net.sf.oval.constraint.NotNull;
import net.sf.oval.constraint.ValidateWithMethod;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.Optional;

/**
 * Schedule for a job that repeats periodically.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class PeriodicSchedule extends BaseSchedule {

    /**
     *
     */
    public enum Period {
        /** */
        HOUR(Duration.ofHours(1)),
        /** */
        DAY(Duration.ofDays(1));

        private Duration _duration;
        Period(final Duration duration) {
            _duration = duration;
        }

        public Duration getDuration() {
            return _duration;
        }

        /**
         * Rounds a time backwards to the nearest natural boundary of this period (e.g. __:00 for HOUR, midnight for DAY).
         *
         * @param time The time to round.
         * @return The rounded time, guaranteed to be on a {period}-boundary in {@code time}'s time zone.
         */
        public ZonedDateTime floor(final ZonedDateTime time) {
            final ZonedDateTime t0 = ZonedDateTime.parse("1970-01-01T00:00:00Z");
            final long dtSeconds = ChronoUnit.SECONDS.between(t0, time);
            final long step = _duration.toMillis() / 1000;
            final long flooredDtSeconds = (dtSeconds / step) * step;
            return t0.plus(Duration.ofSeconds(flooredDtSeconds));
        }

        /**
         * Rounds a time forwards to the nearest natural boundary of this period (e.g. __:00 for HOUR, midnight for DAY).
         *
         * @param time The time to round.
         * @return The rounded time, guaranteed to be on a {period}-boundary in {@code time}'s time zone.
         */
        public ZonedDateTime ceil(final ZonedDateTime time) {
            final ZonedDateTime floor = floor(time);
            if (time.equals(floor)) {
                return floor;
            }
            return floor.plus(_duration);
        }
    }

    private final Period _period;
    private final Duration _offset;

    private PeriodicSchedule(final Builder builder) {
        super(builder);
        _period = builder._period;
        _offset = builder._offset;
    }

    public Period getPeriod() {
        return _period;
    }

    public Duration getOffset() {
        return _offset;
    }

    @Override
    public Optional<ZonedDateTime> unboundedNextRun(final Optional<ZonedDateTime> lastRun) {
        if (!lastRun.isPresent()) {
            return Optional.of(_period.ceil(getRunAtAndAfter()).plus(_offset));
        }
        return Optional.of(_period.floor(lastRun.get()).plus(_period.getDuration()).plus(_offset));
    }


    /**
     * Implementation of builder pattern for {@link OneOffSchedule}.
     *
     * @author Spencer Pearson (spencerpearson at dropbox dot com)
     */
    public static final class Builder extends BaseSchedule.Builder<Builder, PeriodicSchedule> {
        @NotNull
        private Period _period;
        @NotNull
        @ValidateWithMethod(methodName = "validateOffset", parameterType = Duration.class)
        private Duration _offset = Duration.ZERO;

        /**
         * Public constructor.
         */
        public Builder() {
            super(PeriodicSchedule::new);
        }

        @Override
        protected Builder self() {
            return this;
        }

        /**
         * The period with which the schedule fires. Required. Cannot be null.
         *
         * @param period The period.
         * @return This instance of Builder.
         */
        public Builder setPeriod(final Period period) {
            _period = period;
            return this;
        }

        /**
         * The offset from the period start when the schedule should fire. Defaults to zero. Cannot be null.
         * (e.g. {@code Duration.ofHours(2)} to run 2h after the start of the day, if period=DAY.
         *
         * @param offset The offset.
         * @return This instance of Builder.
         */
        public Builder setOffset(final Duration offset) {
            _offset = offset;
            return this;
        }

        @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "invoked reflectively by @ValidateWithMethod")
        private boolean validateOffset(final Duration offset) {
            return !offset.isNegative()
                   && _period.getDuration().toMillis() > offset.toMillis();
        }
    }
}
