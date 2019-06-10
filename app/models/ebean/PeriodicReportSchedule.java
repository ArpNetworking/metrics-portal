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
import models.internal.scheduling.Period;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

/**
 * Data Model for a periodically recurring report schedule (i.e. recurs daily).
 *
 * NOTE: This class is enhanced by Ebean to do things like lazy loading and
 * resolving relationships between beans. Therefore, including functionality
 * which serializes the state of the object can be dangerous (e.g. {@code toString},
 * {@code @Loggable}, etc.).
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@DiscriminatorValue("PERIODIC")
public class PeriodicReportSchedule extends ReportSchedule {

    public static final long serialVersionUID = 1;

    @Column(name = "offset_nanos")
    private long offsetNanos;
    @Column(name = "period")
    @Enumerated(EnumType.STRING)
    private Period period;
    @Column(name = "zone")
    private ZoneId zone;

    public long getOffsetNanos() {
        return offsetNanos;
    }

    public void setOffsetNanos(final long value) {
        offsetNanos = value;
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
        return Objects.equals(getOffsetNanos(), that.getOffsetNanos())
                && getPeriod() == that.getPeriod()
                && Objects.equals(getZone(), that.getZone());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getOffsetNanos(), getPeriod(), getZone());
    }

    @Override
    public Schedule toInternal() {
        return new PeriodicSchedule.Builder()
                .setRunAtAndAfter(getRunAt())
                .setRunUntil(getRunUntil())
                .setOffset(Duration.ofNanos(getOffsetNanos()))
                .setZone(zone)
                .setPeriod(period.toChronoUnit())
                .build();
    }
}
// CHECKSTYLE.ON: MemberNameCheck
