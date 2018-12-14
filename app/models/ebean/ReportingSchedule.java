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

import java.sql.Timestamp;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Data Model for SQL storage of report schedules.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@Entity
@Table(name = "report_schedules", schema = "portal")
// CHECKSTYLE.OFF: MemberNameCheck
public class ReportingSchedule {
    /**
     * The recurrence behaviour of a schedule.
     */
    public enum RecurrenceType {
        /**
         * A non-recurring schedule.
         */
        NONE,
        /**
         * A schedule which recurs daily.
         */
        DAILY,
        /**
         * A schedule which recurs weekly.
         */
        WEEKLY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "send_at")
    private Timestamp sendAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_type")
    private RecurrenceType recurrenceType = RecurrenceType.NONE;

    public Integer getId() {
        return id;
    }

    public Timestamp getSendAt() {
        return sendAt;
    }

    public void setSendAt(final Timestamp value) {
        sendAt = value;
    }

    public RecurrenceType getRecurrenceType() {
        return recurrenceType;
    }

    public void setRecurrenceType(final RecurrenceType value) {
        recurrenceType = value;
    }

    @Override
    public String toString() {
        return "ReportingSchedule{"
                + "id=" + id
                + ", sendAt=" + sendAt
                + ", recurrenceType=" + recurrenceType
                + '}';
    }
}
// CHECKSTYLE.ON: MemberNameCheck
