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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

/**
 * Data Model for SQL storage of groups of report recipients.
 *
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

    @OneToMany(mappedBy = "recipientGroup", cascade = CascadeType.ALL)
    @PrivateOwned
    private List<ReportRecipient> recipients;

    /**
     * Create a new, empty recipient group.
     */
    public ReportRecipientGroup() {
        this.recipients = new ArrayList<>();
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

    public List<ReportRecipient> getRecipients() {
        return recipients;
    }

    /**
     * Add a report recipient to this group.
     *
     * @param recipient - The <code>ReportRecipient</code> to add to this group.
     */
    public void addRecipient(final ReportRecipient recipient) {
        recipients.add(recipient);
    }

    /**
     * Add several report recipients to this group.
     *
     * @param values - The <code>ReportRecipient</code>s to add to this group.
     */
    public void addAllRecipients(final ReportRecipient... values) {
        this.recipients.addAll(Arrays.asList(values));
    }
}
// CHECKSTYLE.ON: MemberNameCheck
