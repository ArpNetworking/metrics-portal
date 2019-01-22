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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import io.ebean.annotation.CreatedTimestamp;
import io.ebean.annotation.SoftDelete;
import io.ebean.annotation.UpdatedTimestamp;
import models.internal.impl.EmailRecipientGroup;
import models.internal.reports.RecipientGroup;

import java.sql.Timestamp;
import java.util.Collection;
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
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.PersistenceException;
import javax.persistence.Table;

/**
 * Data Model for SQL storage of groups of report recipients.
 * <br>
 * A recipient group can contain multiple {@link models.ebean.ReportRecipient} instances.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@Entity
@Table(name = "recipient_groups", schema = "portal")
// CHECKSTYLE.OFF: MemberNameCheck
public final class ReportRecipientGroup {

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

    @ManyToMany(mappedBy = "recipientGroups")
    private Collection<Report> reports;

    @OneToMany(mappedBy = "recipientGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReportRecipient> recipients;

    @OneToMany(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "recipient_group_id", referencedColumnName = "id")
    private Set<ReportFormat> formats;

    @SoftDelete
    @Column(name = "deleted")
    private boolean deleted;

    /**
     * Create a new recipient group.
     */
    public ReportRecipientGroup() {
    }

    public void setId(final Long value) {
        id = value;
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
     * @return The {@code ReportRecipient}s
     */
    public List<ReportRecipient> getRecipients() {
        return recipients;
    }

    /**
     * Set the report recipients for this group.
     *
     * @param value - The new {@code ReportRecipient}s for this group.
     */
    public void setRecipients(final List<ReportRecipient> value) {
        final long distinctTypes =
            value.stream()
                    .map(ReportRecipient::getType)
                    .distinct()
                    .count();
        if (distinctTypes > 1) {
            throw new IllegalArgumentException("A report recipient group must only have one type of recipient");
        }
        recipients = value;
    }

    public Set<ReportFormat> getFormats() {
        return formats;
    }

    public void setFormats(final Set<ReportFormat> value) {
        formats = value;
    }

    /**
     * Convert this model to its internal representation.
     * @return The internal representation of this {@code RecipientGroup}.
     */
    public RecipientGroup toInternal() {
        final ImmutableSet<ReportRecipient.RecipientType> recipientTypes =
                recipients.stream()
                        .map(ReportRecipient::getType)
                        .collect(ImmutableSet.toImmutableSet());

        final Set<ReportRecipient.RecipientType> unsupportedTypes =
                Sets.difference(recipientTypes, ImmutableSet.of(ReportRecipient.RecipientType.EMAIL));
        if (!unsupportedTypes.isEmpty()) {
            throw new PersistenceException("Unsupported recipient type: " + unsupportedTypes);
        }

        final ImmutableSet<String> recipientAddresses =
                recipients
                        .stream()
                        .map(ReportRecipient::get)
                        .collect(ImmutableSet.toImmutableSet());

        final ImmutableList<models.internal.reports.ReportFormat> internalFormats =
                formats.stream()
                        .map(ReportFormat::toInternal)
                        .collect(ImmutableList.toImmutableList());

        return new EmailRecipientGroup.Builder()
                .setId(uuid)
                .setName(name)
                .setEmails(recipientAddresses)
                .setFormats(internalFormats)
                .build();
    }
}
// CHECKSTYLE.ON: MemberNameCheck
