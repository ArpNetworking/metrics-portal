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
        MONTHLY,
    }

    @Column(name = "period")
    @Enumerated(EnumType.STRING)
    private Period period;

    public Period getPeriod() {
        return period;
    }

    public void setPeriod(final Period value) {
        period = value;
    }

    @Override
    public String toString() {
        return toStringHelper()
                .add("period", period)
                .toString();
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
        return super.equals(o) &&
            Objects.equals(getEndDate(), that.getEndDate());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getEndDate());
    }
}
// CHECKSTYLE.ON: MemberNameCheck
