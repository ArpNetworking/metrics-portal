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

package models.ebean;

import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule;

import java.time.Duration;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

/**
 * Data Model for a periodically recurring report schedule (i.e. recurs daily).
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@DiscriminatorValue("PERIODIC")
public class PeriodicReportSchedule extends ReportSchedule {
    @Column(name = "offset_duration")
    private Duration offset;
    @Column(name = "period")
    @Enumerated(EnumType.STRING)
    private Period period;
    @Column(name = "zone")
    private ZoneId zone;

    public Duration getOffset() {
        return offset;
    }

    public void setOffset(final Duration value) {
        offset = value;
    }

    public Period getPeriod() {
        return period;
    }

    public void setPeriod(final Period value) {
        period = value;
    }

    public ZoneId getZone() {
        return zone;
    }

    public void setZone(final ZoneId value) {
        zone = value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PeriodicReportSchedule that = (PeriodicReportSchedule) o;
        return Objects.equals(getOffset(), that.getOffset())
                && getPeriod() == that.getPeriod()
                && Objects.equals(getZone(), that.getZone());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getOffset(), getPeriod(), getZone());
    }

    @Override
    public String toString() {
        return toStringHelper()
                .add("period", period)
                .add("offset", offset)
                .add("zone", zone)
                .toString();
    }

    @Override
    public Schedule toInternal() {
        return new PeriodicSchedule.Builder()
                .setRunAtAndAfter(getRunAt())
                .setRunUntil(getRunUntil())
                .setOffset(getOffset())
                .setZone(zone)
                .setPeriod(period.toChronoUnit())
                .build();
    }

    /**
     * The period with which this schedule recurs.
     */
    public enum Period {
        /**
         * A period of 1 hour.
         */
        HOURLY,
        /**
         * A period of 1 day.
         */
        DAILY,
        /**
         * A period of 1 week.
         */
        WEEKLY,
        /**
         * A period of 1 month.
         */
        MONTHLY;

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
            switch (this) {
                case HOURLY:
                    return ChronoUnit.HOURS;
                case DAILY:
                    return ChronoUnit.DAYS;
                case WEEKLY:
                    return ChronoUnit.WEEKS;
                case MONTHLY:
                    return ChronoUnit.MONTHS;
                default:
                    throw new AssertionError("unreachable branch");
            }
        }
    }
}
// CHECKSTYLE.ON: MemberNameCheck
