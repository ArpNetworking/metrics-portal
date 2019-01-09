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

import com.google.common.base.MoreObjects;

import java.sql.Date;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

/**
 * Data Model for a simple recurring report schedule (i.e. recurs daily).
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@DiscriminatorValue("DAILY")
public class RecurringReportingSchedule extends ReportingSchedule {
    @Column(name = "end_date")
    private Date endDate;

    @Nullable
    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(@Nullable final Date value) {
        endDate = value;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", getId())
                .add("availableAt", getAvailableAt())
                .add("startDate", getStartDate())
                .add("endDate", endDate)
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
        RecurringReportingSchedule that = (RecurringReportingSchedule) o;
        return super.equals(o) &&
            Objects.equals(getEndDate(), that.getEndDate());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getEndDate());
    }
}
// CHECKSTYLE.ON: MemberNameCheck
