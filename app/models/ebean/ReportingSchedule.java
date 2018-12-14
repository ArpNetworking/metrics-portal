/**
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

import javax.persistence.*;
import java.sql.Timestamp;

@Entity
@Table(name = "report_schedules", schema = "portal")
public class ReportingSchedule {
    public Integer getId() {
        return id;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "valid_after")
    private Timestamp validAfter;

    @Column(name = "send_at")
    private Timestamp sendAt;

    @Column(name = "is_recurring")
    private boolean recurring;

    public Timestamp getValidAfter() {
        return validAfter;
    }

    public void setValidAfter(Timestamp validAfter) {
        this.validAfter = validAfter;
    }

    public Timestamp getSendAt() {
        return sendAt;
    }

    public void setSendAt(Timestamp sendAt) {
        this.sendAt = sendAt;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    @Override
    public String toString() {
        return "ReportingSchedule{" +
                "id=" + id +
                ", validAfter=" + validAfter +
                ", sendAt=" + sendAt +
                ", recurring=" + recurring +
                '}';
    }
}
