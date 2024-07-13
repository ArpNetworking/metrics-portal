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

import com.arpnetworking.logback.annotations.Loggable;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.base.MoreObjects;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.sf.oval.constraint.NotNull;
import net.sf.oval.constraint.ValidateWithMethod;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.Objects;
import java.util.Optional;

/**
 * Schedule for a job that repeats periodically within some bounded window of time.
 * <p>
 * This schedule respects backfills. If the lastRun is not the most recent period,
 * the schedule will yield all periods since until it is caught up.
 * <p>
 * If this behavior is not required and you only care that a job attempts to execute
 * periodically, you should instead use a {@code UnboundedPeriodicSchedule}.
 *
 * @see UnboundedPeriodicSchedule
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
@Loggable
public final class PeriodicSchedule extends BoundedSchedule {

    private final ChronoUnit _period;
    private final long _periodCount;
    private final ZoneId _zone;
    private final Duration _offset;
    private static final Logger LOGGER = LoggerFactory.getLogger(PeriodicSchedule.class);

    private PeriodicSchedule(final Builder builder) {
        super(builder);
        _period = builder._period;
        _periodCount = builder._periodCount;
        _zone = builder._zone;
        _offset = builder._offset;
    }

    @Override
    protected Optional<Instant> unboundedNextRun(final Optional<Instant> lastRun) {
        final Instant untruncatedNextRun = lastRun
                .map(run -> run.plus(_periodCount, _period))
                .orElseGet(this::getRunAtAndAfter);

        try {
            final Instant nextRun =
                    ZonedDateTime.ofInstant(untruncatedNextRun, _zone)
                            .truncatedTo(_period)
                            .plus(_offset)
                            .toInstant();

            return Optional.of(nextRun);
        } catch (final UnsupportedTemporalTypeException e) {
            LOGGER.error().setMessage("Error creating next run time")
                    .addData("lastRun", lastRun)
                    .addData("untruncatedNextRun", untruncatedNextRun)
                    .addData("period", _period)
                    .addData("periodCount", _periodCount)
                    .addData("zone", _zone)
                    .addData("offset", _offset)
                    .setThrowable(e)
                    .log();
            throw e;
        }
    }

    @Override
    public <T> T accept(final Visitor<T> visitor) {
        return visitor.visitPeriodic(this);
    }

    public ChronoUnit getPeriod() {
        return _period;
    }

    public long getPeriodCount() {
        return _periodCount;
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
        return Objects.equals(getPeriod(), that.getPeriod())
                && Objects.equals(getPeriodCount(), that.getPeriodCount())
                && Objects.equals(getZone(), that.getZone())
                && Objects.equals(getOffset(), that.getOffset());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getPeriod(), getPeriodCount(), getZone(), getOffset());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("period", _period)
                .add("periodCount", _periodCount)
                .add("offset", _offset)
                .add("zone", _zone)
                .add("start", getRunAtAndAfter())
                .add("end", getRunUntil())
                .toString();
    }

    /**
     * Implementation of builder pattern for {@link PeriodicSchedule}.
     *
     * @author Spencer Pearson (spencerpearson at dropbox dot com)
     */
    public static final class Builder extends BoundedSchedule.Builder<Builder, PeriodicSchedule> {
        private long _periodCount = 1;
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
         * The number of periods with which the schedule fires. Defaults to 1.
         *
         * @param periodCount The period count.
         * @return This instance of Builder.
         */
        public Builder setPeriodCount(final long periodCount) {
            _periodCount = periodCount;
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
         *
         * The offset must be strictly smaller than a single period.
         *
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
