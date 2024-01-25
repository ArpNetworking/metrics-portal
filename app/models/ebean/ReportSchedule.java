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
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

import java.time.Instant;
import javax.annotation.Nullable;

/**
 * Data Model for SQL storage of report schedules.
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
@Table(name = "report_schedules", schema = "portal")
@DiscriminatorColumn(name = "type")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class ReportSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "run_at")
    private Instant runAt;

    @Nullable
    @Column(name = "run_until")
    private Instant runUntil;

    /* package */ Instant getRunAt() {
        return runAt;
    }

    public void setRunAt(final Instant value) {
        runAt = value;
    }

    @Nullable
    /* package */ Instant getRunUntil() {
        return runUntil;
    }

    public void setRunUntil(@Nullable final Instant value) {
        runUntil = value;
    }

    public void setId(final Integer value) {
        id = value;
    }

    public Integer getId() {
        return id;
    }

    /**
     * Convert this schedule to its internal representation.
     *
     * @return the internal representation of this schedule.
     */
    public abstract Schedule toInternal();
}
// CHECKSTYLE.ON: MemberNameCheck
