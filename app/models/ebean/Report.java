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

import io.ebean.annotation.CreatedTimestamp;
import io.ebean.annotation.UpdatedTimestamp;

import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;


/**
 * Data Model for SQL storage of a Report.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@Entity
@Table(name = "reports", schema = "portal")
// CHECKSTYLE.OFF: MemberNameCheck
public class Report {
    /**
     * The result of the report execution.
     */
    // TODO(cbriones): This should go into Job the interface.
    public enum State {
        /**
         * The job executed successfully.
         */
        SUCCESS,
        /**
         * The job failed.
         */
        FAILURE,
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column
    private UUID uuid;

    @Column
    private String name;

    @JoinColumn(name = "report_source_id")
    @ManyToOne(optional = false, cascade = CascadeType.PERSIST)
    private ReportSource reportSource;

    @OneToMany(cascade = CascadeType.PERSIST)
    private Set<ReportRecipientGroup> recipientGroups;

    @CreatedTimestamp
    @Column(name = "created_at")
    private Timestamp createdAt;

    @UpdatedTimestamp
    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @OneToOne(optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "report_schedule_id")
    private ReportSchedule schedule;

    @Column(name = "disabled")
    private boolean disabled;

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(final UUID value) {
        uuid = value;
    }

    public ReportSource getReportSource() {
        return reportSource;
    }

    public void setReportSource(final ReportSource value) {
        reportSource = value;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public String getName() {
        return name;
    }

    public void setName(final String value) {
        name = value;
    }

    public Long getId() {
        return id;
    }

    public ReportSchedule getSchedule() {
        return schedule;
    }

    public void setSchedule(final ReportSchedule value) {
        schedule = value;
    }

    public Set<ReportRecipientGroup> getRecipientGroups() {
        return recipientGroups;
    }

    public void setRecipientGroups(final Set<ReportRecipientGroup> value) {
        recipientGroups = value;
    }

    public boolean getDisabled() {
        return disabled;
    }
}
// CHECKSTYLE.ON: MemberNameCheck
