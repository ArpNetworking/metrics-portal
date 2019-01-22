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

import com.google.common.collect.ImmutableSet;
import io.ebean.annotation.CreatedTimestamp;
import io.ebean.annotation.SoftDelete;
import io.ebean.annotation.UpdatedTimestamp;
import models.internal.impl.DefaultReport;
import models.internal.reports.RecipientGroup;

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
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
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

    @ManyToMany
    @JoinTable(
            name = "reports_to_recipient_groups",
            schema = "portal",
            joinColumns = @JoinColumn(name = "recipient_group_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "report_id", referencedColumnName = "id")
    )
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

    @SoftDelete
    @Column(name = "deleted")
    private boolean deleted;

    @ManyToOne(optional = false)
    @Column(name = "organization_id")
    private Organization organization;

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

    public void setId(final Long value) {
        id = value;
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

    public boolean getDeleted() {
        return deleted;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(final Organization value) {
        organization = value;
    }


    /**
     * Transform this object into its internal representation.
     *
     * @return The internal representation of this {@code Report}.
     */
    public models.internal.reports.Report toInternal() {
        final ImmutableSet<RecipientGroup> groups =
                recipientGroups
                        .stream()
                        .map(ReportRecipientGroup::toInternal)
                        .collect(ImmutableSet.toImmutableSet());

        return new DefaultReport.Builder()
                .setId(uuid)
                .setName(name)
                .setRecipientGroups(groups)
                .setSchedule(schedule.toInternal())
                .setReportSource(reportSource.toInternal())
                .build();
    }
}
// CHECKSTYLE.ON: MemberNameCheck
