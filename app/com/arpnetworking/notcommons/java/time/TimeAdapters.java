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

package com.arpnetworking.notcommons.java.time;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * Utilities for converting between time representations.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class TimeAdapters {

    private TimeAdapters() {}

    /**
     * Convert a {@code TimeUnit} to the corresponding {@code ChronoUnit}.
     *
     * @apiNote
     * This method was added because no native conversion method was available.
     * JDK9 introduces {@code TimeUnit#toChronoUnit}, which provides
     * equivalent functionality.
     * <p>
     * As such this will be deprecated/removed when that is available.
     *
     * @param timeUnit The unit to convert.
     * @return The equivalent {@code ChronoUnit}.
     */
    public static ChronoUnit toChronoUnit(final TimeUnit timeUnit) {
        switch (timeUnit) {
            case NANOSECONDS:
                return ChronoUnit.NANOS;
            case MICROSECONDS:
                return ChronoUnit.MICROS;
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
            default:
                throw new IllegalStateException("Unknown TimeUnit");
        }
    }
}
