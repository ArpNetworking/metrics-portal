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
import com.google.common.collect.ImmutableSetMultimap;
import io.ebean.EbeanServer;
import io.ebean.annotation.CreatedTimestamp;
import io.ebean.annotation.SoftDelete;
import io.ebean.annotation.UpdatedTimestamp;
import models.internal.impl.DefaultReport;
import org.apache.commons.codec.digest.DigestUtils;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
 * NOTE: This class is enhanced by Ebean to do things like lazy loading and
 * resolving relationships between beans. Therefore, including functionality
 * which serializes the state of the object can be dangerous (e.g. {@code toString},
 * {@code @Loggable}, etc.).
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

    @OneToMany(
            mappedBy = "report",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ReportRecipientAssoc> recipientAssocs = Collections.emptyList();

    @CreatedTimestamp
    @Column(name = "created_at")
    private Timestamp createdAt;

    @UpdatedTimestamp
    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @OneToOne(optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "report_schedule_id")
    private ReportSchedule schedule;

    @Column(name = "timeout_nanos")
    private long timeoutNanos;

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

    public Long getId() {
        return id;
    }

    public void setId(final Long value) {
        id = value;
    }

    public ReportSchedule getSchedule() {
        return schedule;
    }

    public void setSchedule(final ReportSchedule value) {
        schedule = value;
    }

    public long getTimeout() {
        return timeoutNanos;
    }

    public void setTimeout(final long value) {
        timeoutNanos = value;
    }

    public Set<Recipient> getRecipients() {
        return recipientAssocs
                .stream()
                .map(ReportRecipientAssoc::getRecipient)
                .collect(ImmutableSet.toImmutableSet());
    }

    public void setRecipients(final ImmutableSetMultimap<ReportFormat, Recipient> values) {
        recipientAssocs = values
                .entries()
                .stream()
                .map(entry -> {
                    final ReportFormat format = entry.getKey();
                    final Recipient recipient = entry.getValue();
                    final ReportRecipientAssoc assoc = new ReportRecipientAssoc();
                    assoc.setFormat(format);
                    assoc.setRecipient(recipient);
                    assoc.setReport(this);
                    return assoc;
                })
                .collect(Collectors.toList());
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
     * Finds a {@link Report} when given a uuid.
     *
     * @param ebeanServer The {@code EbeanServer} instance to use.
     * @param organization The parent organization for this report.
     * @param reportId The report uuid to lookup.
     * @return The report from the database.
     */
    public static Optional<Report> findByUUID(
            final EbeanServer ebeanServer,
            final models.ebean.Organization organization,
            final UUID reportId) {
        return ebeanServer.find(models.ebean.Report.class)
                .where()
                .eq("uuid", reportId)
                .eq("organization.uuid", organization.getUuid())
                .findOneOrEmpty();
    }

    /**
     * Transform this object into its internal representation.
     *
     * @return The internal representation of this {@code Report}.
     */
    public models.internal.reports.Report toInternal() {
        final ImmutableSetMultimap<models.internal.reports.ReportFormat, models.internal.reports.Recipient> internalRecipients =
            recipientAssocs
                    .stream()
                    .collect(ImmutableSetMultimap.toImmutableSetMultimap(this::assocToFormat, this::assocToRecipient));

        // The ETag should change if:
        // - the Report changes (in which case, its timestamp will change); or
        // - the Source changes (in which case, its timestamp will change); or
        // - any of the (mutable) recipients is updated (in which case, its timestamp will change); or
        // - the set of recipients changes (in which case, the set of UUIDs will change).
        // So the ETag should be something like a hash of all those quantities.
        final StringBuilder eTagBuilder = new StringBuilder()
                .append(getUpdatedAt())
                .append(reportSource.getUpdatedAt());
        recipientAssocs
                .stream()
                .map(ReportRecipientAssoc::getRecipient)
                .sorted(Comparator.comparing(Recipient::getId))
                .forEach(recipient -> {
                    eTagBuilder.append(recipient.getId());
                    eTagBuilder.append(recipient.getUpdatedAt());
                });
        final String eTag = DigestUtils.sha1Hex(eTagBuilder.toString());

        return new DefaultReport.Builder()
                .setId(uuid)
                .setETag(eTag)
                .setName(name)
                .setRecipients(internalRecipients)
                .setSchedule(schedule.toInternal())
                .setTimeout(Duration.ofNanos(timeoutNanos))
                .setReportSource(reportSource.toInternal())
                .build();
    }

    private models.internal.reports.ReportFormat assocToFormat(final ReportRecipientAssoc assoc) {
        return assoc.getFormat().toInternal();
    }

    private models.internal.reports.Recipient assocToRecipient(final ReportRecipientAssoc assoc) {
        return assoc.getRecipient().toInternal();
    }
}
// CHECKSTYLE.ON: MemberNameCheck
