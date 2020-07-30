/*
 * Copyright 2020 Dropbox, Inc.
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

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.logback.annotations.Loggable;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import net.sf.oval.constraint.Min;
import net.sf.oval.constraint.NotNull;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * A schedule for a job that repeats periodically, without any bounds or regard
 * for the last completed time.
 * <p>
 * The next run for a unbounded periodic schedule is always the start of the
 * most recent period of the schedule, as aligned with the start of the epoch.
 * <p>
 * <b>WARNING:</b>
 * This behavior means that jobs with this schedule will ignore previously missed runs
 * - if you need backfills you should instead use a standard {@link PeriodicSchedule}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@Loggable
public final class UnboundedPeriodicSchedule implements Schedule {

    private final ChronoUnit _period;
    private final long _periodCount;
    private final Clock _clock;

    private UnboundedPeriodicSchedule(final Builder builder) {
        _period = builder._period;
        _periodCount = builder._periodCount;
        _clock = builder._clock;
    }

    @Override
    public Optional<Instant> nextRun(final Optional<Instant> lastRun) {
        final Duration fullPeriod = Duration.of(_periodCount, _period);

        final Instant now = _clock.instant();
        final Instant start = lastRun
                .filter(run -> run.compareTo(now) >= 0)
                .map(run -> run.plus(fullPeriod)) // Avoid repeating the same period.
                .orElse(now);

        final long epochMillis = start.toEpochMilli();
        final Instant nextRun = Instant.ofEpochMilli((epochMillis / fullPeriod.toMillis()) * fullPeriod.toMillis());
        return Optional.of(nextRun);
    }

    @Override
    public <T> T accept(final Visitor<T> visitor) {
        return visitor.visitUnboundedPeriodic(this);
    }

    public ChronoUnit getPeriod() {
        return _period;
    }

    public long getPeriodCount() {
        return _periodCount;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UnboundedPeriodicSchedule that = (UnboundedPeriodicSchedule) o;
        return _periodCount == that._periodCount
                && _period == that._period
                && Objects.equal(_clock, that._clock);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(_period, _periodCount, _clock);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("_period", _period)
                .add("_periodCount", _periodCount)
                .add("_clock", _clock)
                .toString();
    }

    /**
     * Implementation of builder pattern for {@link UnboundedPeriodicSchedule}.
     *
     * @author Christian Briones (cbriones at dropbox dot com)
     */
    public static final class Builder extends OvalBuilder<UnboundedPeriodicSchedule> {
        @Min(1)
        private long _periodCount = 1;
        @NotNull
        private ChronoUnit _period;
        @NotNull
        private Clock _clock = Clock.systemUTC();

        /**
         * Default constructor.
         */
        public Builder() {
            super(UnboundedPeriodicSchedule::new);
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
         * The clock to use. Defaults to {@link Clock#systemUTC()}.
         *
         * Should only be used for testing purposes.
         *
         * @param clock The clock to use.
         * @return This instance of Builder.
         */
        @VisibleForTesting
        protected Builder setClock(final Clock clock) {
            _clock = clock;
            return this;
        }
    }
}
