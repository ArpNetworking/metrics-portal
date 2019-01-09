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
        final Instant runAfter = lastRun.orElse(getRunAtAndAfter().minus(Duration.ofNanos(1)));
        return Optional.of(nextAlignedBoundaryAfter(runAfter).plus(_offset));
    }

    /**
     * Returns the first instant after the given boundary that is [period]-aligned in our time zone.
     *
     * @param boundary The exclusive lower bound.
     * @return A boundary that is guaranteed to be [period]-aligned and after the given boundary.
     */
    private Instant nextAlignedBoundaryAfter(final Instant boundary) {
        final ZonedDateTime zonedLastRun = ZonedDateTime.ofInstant(boundary, _zone);
        ZonedDateTime aligned = zonedLastRun.truncatedTo(_period);
        aligned = aligned.plus(_period.getDuration()).truncatedTo(_period);
        if (!aligned.toInstant().isAfter(boundary)) {
            aligned = aligned.plus(_period.getDuration()).truncatedTo(_period);
        }
        if (!aligned.toInstant().isAfter(boundary)) {
            aligned = aligned.plus(_period.getDuration().multipliedBy(2)).truncatedTo(_period);
        }
        if (!aligned.toInstant().isAfter(boundary)) {
            aligned = aligned.plus(_period.getDuration().multipliedBy(3)).truncatedTo(_period);
        }
        if (!aligned.toInstant().isAfter(boundary)) {
            throw new AssertionError(String.format(
                    "can't find next [%s]-aligned moment after [%s]",
                    _period,
                    boundary));
        }
        return aligned.toInstant();
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
        protected ZoneId _zone;
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
                   && _period.getDuration().toMillis() > offset.toMillis();
        }
    }
}
