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
package com.arpnetworking.kairos.client.models;

import com.fasterxml.jackson.annotation.JsonValue;

import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Enumeration representing the possible values for a Kairos sampling unit.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public enum SamplingUnit {
    /**
     * Sampling is count represents milliseconds.
     */
    MILLISECONDS,
    /**
     * Sampling is count represents seconds.
     */
    SECONDS,
    /**
     * Sampling is count represents minutes.
     */
    MINUTES,
    /**
     * Sampling is count represents hours.
     */
    HOURS,
    /**
     * Sampling is count represents days.
     */
    DAYS,
    /**
     * Sampling is count represents weeks.
     */
    WEEKS,
    /**
     * Sampling is count represents months.
     */
    MONTHS,
    /**
     * Sampling is count represents years.
     */
    YEARS;

    /**
     * Encode the enumeration value as lower case.
     *
     * NOTE: KairosDb accepts either upper or lower case representation. This
     * model is otherwise a pass-through but converts all enumeration values
     * to lower case.
     *
     * @return json encoded value
     */
    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.getDefault());
    }

    /**
     * Convert a sampling unit to the equivalent {@code ChronoUnit}.
     *
     * @param unit The sampling unit to convert.
     * @return A ChronoUnit representing the same unit of time.
     */
    public static ChronoUnit toChronoUnit(final SamplingUnit unit) {
        switch (unit) {
            case MILLISECONDS:
                return ChronoUnit.MILLIS;
            case SECONDS:
                return ChronoUnit.SECONDS;
            case MINUTES:
                return ChronoUnit.MINUTES;
            case HOURS:
                return ChronoUnit.HOURS;
            case DAYS:
                return ChronoUnit.DAYS;
            case WEEKS:
                return ChronoUnit.WEEKS;
            case MONTHS:
                return ChronoUnit.MONTHS;
            case YEARS:
                return ChronoUnit.YEARS;
        }
        throw new IllegalStateException("Unknown value: " + unit);
    }
}
