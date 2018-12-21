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
import io.ebean.annotation.PrivateOwned;
import io.ebean.annotation.UpdatedTimestamp;

import java.sql.Timestamp;
import java.util.List;
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
import javax.persistence.Table;

/**
 * Data Model for SQL storage of groups of report recipients.
 * <p>
 * A recipient group can contain multiple {@link models.ebean.ReportRecipient} instances.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@Entity
@Table(name = "report_recipient_groups", schema = "portal")
// CHECKSTYLE.OFF: MemberNameCheck
public class ReportRecipientGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Long id;

    @Column(name = "uuid")
    private UUID uuid;

    @Column(name = "name")
    private String name;

    @CreatedTimestamp
    @Column(name = "created_at")
    private Timestamp createdAt;

    @UpdatedTimestamp
    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @ManyToOne
    @JoinColumn(name = "reporting_job_id")
    private ReportingJob job;

    @OneToMany(mappedBy = "recipientGroup", cascade = CascadeType.ALL)
    @PrivateOwned
    private List<ReportRecipient> recipients;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "recipient_group_id", referencedColumnName = "id")
    private Set<ReportFormat> formats;

    /**
     * Create a new recipient group.
     */
    public ReportRecipientGroup() {
    }

    public Long getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(final UUID value) {
        uuid = value;
    }

    public String getName() {
        return name;
    }

    public void setName(final String value) {
        name = value;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Get the report recipients for this group.
     *
     * @return The <code>ReportRecipient</code>s
     */
    public List<ReportRecipient> getRecipients() {
        return recipients;
    }

    /**
     * Set the report recipients for this group.
     *
     * @param value - The new <code>ReportRecipient</code>s for this group.
     */
    public void setRecipients(final List<ReportRecipient> value) {
        recipients = value;
    }

    public Set<ReportFormat> getFormats() {
        return formats;
    }

    public void setFormats(final Set<ReportFormat> value) {
        formats = value;
    }
}
// CHECKSTYLE.ON: MemberNameCheck
