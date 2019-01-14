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
import net.sf.oval.constraint.NotNull;
import net.sf.oval.constraint.ValidateWithMethod;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * Schedule for a job that repeats periodically.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class PeriodicSchedule extends BaseSchedule {

    private final ChronoUnit _period;
    private final ZoneId _zone;
    private final Duration _offset;

    private PeriodicSchedule(final Builder builder) {
        super(builder);
        _period = builder._period;
        _zone = builder._zone;
        _offset = builder._offset;
    }

    @Override
    protected Optional<Instant> unboundedNextRun(final Optional<Instant> lastRun) {
        final ZonedDateTime nextAlignedBoundary;
        if (lastRun.isPresent()) {
            final ZonedDateTime zonedLastRun = ZonedDateTime.ofInstant(lastRun.get(), _zone);
            nextAlignedBoundary = _period.addTo(zonedLastRun.truncatedTo(_period), 1);
        } else {
            final ZonedDateTime zonedRunAt = ZonedDateTime.ofInstant(getRunAtAndAfter(), _zone);
            final ZonedDateTime alignedRunAt = zonedRunAt.truncatedTo(_period);
            if (alignedRunAt.toInstant().isBefore(getRunAtAndAfter())) {
                nextAlignedBoundary = _period.addTo(alignedRunAt, 1);
            } else {
                nextAlignedBoundary = alignedRunAt;
            }
        }
        return Optional.of(nextAlignedBoundary.plus(_offset).toInstant());
    }

    public ChronoUnit getPeriod() {
        return _period;
    }

    public ZoneId getZone() {
        return _zone;
    }

    public Duration getOffset() {
        return _offset;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final PeriodicSchedule that = (PeriodicSchedule) o;
        return getPeriod() == that.getPeriod()
                && Objects.equals(getZone(), that.getZone())
                && Objects.equals(getOffset(), that.getOffset());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getPeriod(), getZone(), getOffset());
    }


    /**
     * Implementation of builder pattern for {@link OneOffSchedule}.
     *
     * @author Spencer Pearson (spencerpearson at dropbox dot com)
     */
    public static final class Builder extends BaseSchedule.Builder<Builder, PeriodicSchedule> {
        @NotNull
        private ChronoUnit _period;
        @NotNull
        private ZoneId _zone;
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
        public Builder setPeriod(final ChronoUnit period) {
            _period = period;
            return this;
        }

        /**
         * The time zone the times should be computed in. Required. Cannot be null.
         *
         * @param zone The time zone.
         * @return This instance of {@code Builder}.
         */
        public Builder setZone(final ZoneId zone) {
            _zone = zone;
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
                   && offset.minus(_period.getDuration()).isNegative();
        }
    }
}
