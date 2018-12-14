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

import io.ebean.annotation.CreatedTimestamp;
import io.ebean.annotation.UpdatedTimestamp;

import javax.annotation.Nullable;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import java.sql.Timestamp;
import java.util.UUID;

@Entity
@Table(name = "reporting_jobs", schema = "portal")
public class ReportingJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column
    private UUID uuid;

    @Column
    private String name;

    @JoinColumn(name = "report_source_id")
    @ManyToOne(optional = false)
    private ReportSource reportSource;

    @JoinColumn(name = "recipient_group_id")
    @ManyToOne(optional = false)
    private ReportRecipientGroup recipientGroup;

    @CreatedTimestamp
    @Column(name = "created_at")
    private Timestamp createdAt;

    @UpdatedTimestamp
    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @OneToOne(optional = false, cascade = CascadeType.ALL)
    @JoinColumn(name = "report_schedule_id")
    private ReportingSchedule schedule;

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public ReportSource getReportSource() {
        return reportSource;
    }

    public void setReportSource(ReportSource reportSource) {
        this.reportSource = reportSource;
    }

    public ReportRecipientGroup getRecipientGroup() {
        return recipientGroup;
    }

    public void setRecipientGroup(ReportRecipientGroup recipientGroup) {
        this.recipientGroup = recipientGroup;
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

    public void setName(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public ReportingSchedule getSchedule() {
        return schedule;
    }

    public void setSchedule(@Nullable ReportingSchedule schedule) {
        this.schedule = schedule;
    }
}
