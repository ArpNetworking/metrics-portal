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
import java.sql.Timestamp;
import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Table;

/**
 * Data Model for SQL storage of report schedules.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@Table(name = "report_schedules", schema = "portal")
@DiscriminatorColumn(name = "type")
@DiscriminatorValue("ONE_OFF")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public class ReportingSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "start_date")
    private Date startDate;

    @Column(name = "available_at")
    private Timestamp availableAt;

    public Timestamp getAvailableAt() {
        return availableAt;
    }

    public void setAvailableAt(final Timestamp value) {
        availableAt = value;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(final Date value) {
        startDate = value;
    }

    public void setId(final Integer value) {
        id = value;
    }

    public Integer getId() {
        return id;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("startDate", startDate)
                .add("availableAt", availableAt)
                .toString();
    }
}
// CHECKSTYLE.ON: MemberNameCheck
