/*
 * Copyright 2019 Dropbox, Inc.
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

package models.internal.scheduling;

import java.time.temporal.ChronoUnit;

/**
 * The period with which a schedule recurs.
 *
 * @author Christian Briones (cbriones at dropbox dot com).
 */
public enum Period {
    /**
     * A period of 1 hour.
     */
    HOURLY(ChronoUnit.HOURS),
    /**
     * A period of 1 day.
     */
    DAILY(ChronoUnit.DAYS),
    /**
     * A period of 1 week.
     */
    WEEKLY(ChronoUnit.WEEKS),
    /**
     * A period of 1 month.
     */
    MONTHLY(ChronoUnit.MONTHS);

    Period(final ChronoUnit unit) {
        this._unit = unit;
    }

    /**
     * Convert a {@code ChronoUnit} to a {@code Period}.
     *
     * @param unit the chronounit to convert.
     * @return the analogous {@code Period} instance
     */
    public static Period fromChronoUnit(final ChronoUnit unit) {
        switch (unit) {
            case HOURS:
                return Period.HOURLY;
            case DAYS:
                return Period.DAILY;
            case WEEKS:
                return Period.WEEKLY;
            case MONTHS:
                return Period.MONTHLY;
            default:
                throw new IllegalArgumentException("Unsupported ChronoUnit: " + unit.toString());
        }
    }

    /**
     * Convert this period to a ChronoUnit.
     *
     * @return the analogous {@code ChronoUnit} instance
     */
    public ChronoUnit toChronoUnit() {
        return this._unit;
    }

    private final ChronoUnit _unit;
}
