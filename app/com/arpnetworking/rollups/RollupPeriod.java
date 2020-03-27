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

import com.arpnetworking.kairos.client.models.SamplingUnit;
import com.google.common.collect.ImmutableList;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Enumeration representing rollup periods.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public enum RollupPeriod {
    /**
     * Hourly rollup period.
     */
    HOURLY("_1h", ChronoUnit.HOURS, SamplingUnit.HOURS),
    /**
     * Daily rollup period.
     */
    DAILY("_1d", ChronoUnit.DAYS, SamplingUnit.DAYS);


    public String getSuffix() {
        return _suffix;
    }

    /**
     * Calculates the most recent period end aligned time relative to the instant provided.
     *
     * @param time instant to use when calculating recent end time
     * @return most recent end time for supplied instant
     */
    public Instant recentEndTime(final Instant time) {
        return time.truncatedTo(_truncationUnit);
    }

    /**
     * Calculates the most recent period start aligned time relative to the instant provided.
     *
     * @param time instant to use when calculating recent start time
     * @return most recent end time for supplied instant
     */
    public Instant recentStartTime(final Instant time) {
        return recentEndTime(time).minus(1, _truncationUnit);
    }

    /**
     * Calculates the next period aligned time relative to the instant provided.
     *
     * @param time instant to use when calculating start time
     * @return next period start to for supplied instant
     */
    public Instant nextPeriodStart(final Instant time) {
        return time.truncatedTo(_truncationUnit)
                .plus(1, _truncationUnit);
    }

    /**
     * Calculates the duration that represents a count of periods.
     *
     * @param count number of periods in duration
     * @return duration of count periods
     */
    public Duration periodCountToDuration(final int count) {
        return _truncationUnit.getDuration().multipliedBy(count);
    }

    public SamplingUnit getSamplingUnit() {
        return _samplingUnit;
    }

    RollupPeriod(final String suffix, final ChronoUnit truncationUnit, final SamplingUnit samplingUnit) {
        _suffix = suffix;
        _truncationUnit = truncationUnit;
        _samplingUnit = samplingUnit;
    }

    /**
     * Get the next smallest rollup period, if any.
     *
     * @return An {@code Optional} containing the next smallest RollupPeriod, or {@link Optional#empty()}
     * if this is already the smallest.
     */
    public Optional<RollupPeriod> nextSmallest() {
        final int i = this.ordinal();
        if (i == 0) {
            return Optional.empty();
        }
        return Optional.of(VALUES.get((i - 1) % VALUES.size()));
    }

    // values() will create a new array on every call to nextSmallest without this.
    private static final List<RollupPeriod> VALUES = ImmutableList.copyOf(values());

    private final String _suffix;
    private final ChronoUnit _truncationUnit;
    private final SamplingUnit _samplingUnit;
}
