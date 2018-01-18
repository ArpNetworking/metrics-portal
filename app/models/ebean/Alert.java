/**
 * Copyright 2015 Groupon.com
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
import models.internal.impl.DefaultAlert;
import org.joda.time.Period;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * Data model for alerts.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@Table(name = "alerts", schema = "portal")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @CreatedTimestamp
    @Column(name = "created_at")
    private Timestamp createdAt;

    @UpdatedTimestamp
    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @Column(name = "uuid")
    private UUID uuid;

    @Column(name = "name")
    private String name;

    @Lob
    @Column(name = "query")
    private String query;

    @Column(name = "period_in_seconds")
    private int periodInSeconds;

    @OneToOne(mappedBy = "alert", cascade = CascadeType.ALL)
    private NagiosExtension nagiosExtension;

    @ManyToOne(optional = false)
    @JoinColumn(name = "organization")
    private Organization organization;

    public Long getId() {
        return id;
    }

    public void setId(final Long value) {
        id = value;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(final Long value) {
        version = value;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Timestamp value) {
        createdAt = value;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final Timestamp value) {
        updatedAt = value;
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

    public String getQuery() {
        return query;
    }

    public void setQuery(final String value) {
        query = value;
    }

    public int getPeriod() {
        return periodInSeconds;
    }

    public void setPeriod(final int value) {
        periodInSeconds = value;
    }

    public NagiosExtension getNagiosExtension() {
        return nagiosExtension;
    }

    public void setNagiosExtension(final NagiosExtension value) {
        nagiosExtension = value;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(final Organization organizationValue) {
        this.organization = organizationValue;
    }

    /**
     * Converts this model into an {@link models.internal.Alert}.
     *
     * @return a new internal model
     */
    public models.internal.Alert toInternal() {
        final DefaultAlert.Builder builder = new DefaultAlert.Builder()
                .setId(getUuid())
                .setOrganization(organization.toInternal())
                .setName(getName())
                .setQuery(getQuery())
                .setPeriod(Period.seconds(getPeriod()).normalizedStandard())
                .setNagiosExtension(Optional.ofNullable(getNagiosExtension()).map(NagiosExtension::toInternal).orElse(null));

        return builder.build();
    }

}
// CHECKSTYLE.ON: MemberNameCheck
