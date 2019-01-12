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
                    throw new RuntimeException("unreachable");
            }
        }

        public static Period fromChronoUnit(final ChronoUnit unit) {
            if (unit.equals(ChronoUnit.HOURS)) {
                return HOURLY;
            } else if (unit.equals(ChronoUnit.DAYS)) {
                return DAILY;
            } else if (unit.equals(ChronoUnit.WEEKS)) {
                return WEEKLY;
            } else if (unit.equals(ChronoUnit.MONTHS)) {
                return MONTHLY;
            }
            throw new IllegalArgumentException("Unsupported ChronoUnit: " + unit.toString());
        }
    }

    @Column(name = "offset_duration")
    private Duration offset;

    public Duration getOffset() {
        return offset;
    }

    public void setOffset(final Duration value) {
        offset = value;
    }

    @Column(name = "period")
    @Enumerated(EnumType.STRING)
    private Period period;

    @Column(name = "zone")
    private ZoneId zone;

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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PeriodicReportSchedule that = (PeriodicReportSchedule) o;
        return Objects.equals(getOffset(), that.getOffset()) &&
                getPeriod() == that.getPeriod() &&
                Objects.equals(getZone(), that.getZone());
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
}
// CHECKSTYLE.ON: MemberNameCheck
