/*
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


import io.ebean.annotation.WhenCreated;
import io.ebean.annotation.WhenModified;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import models.internal.MetricsSoftwareState;
import models.internal.impl.DefaultHost;

import java.sql.Timestamp;
import javax.annotation.Nullable;

/**
 * Data model for hosts.
 *
 * NOTE: This class is enhanced by Ebean to do things like lazy loading and
 * resolving relationships between beans. Therefore, including functionality
 * which serializes the state of the object can be dangerous (e.g. {@code toString},
 * {@code @Loggable}, etc.).
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@Table(name = "hosts", schema = "portal")
public class Host {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @WhenCreated
    @Column(name = "created_at")
    private Timestamp createdAt;

    @WhenModified
    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @Column(name = "name")
    private String name;

    @Column(name = "cluster")
    private String cluster;

    @Column(name = "metrics_software_state")
    private String metricsSoftwareState;

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

    public String getName() {
        return name;
    }

    public void setName(final String value) {
        name = value;
    }

    @Nullable
    public String getCluster() {
        return cluster;
    }

    public void setCluster(@Nullable final String value) {
        cluster = value;
    }

    @Nullable
    public String getMetricsSoftwareState() {
        return metricsSoftwareState;
    }

    public void setMetricsSoftwareState(@Nullable final String value) {
        metricsSoftwareState = value;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(final Organization organizationValue) {
        this.organization = organizationValue;
    }

    /**
     * Converts the EBean model to an internal model.
     *
     * @return an internal model
     */
    public models.internal.Host toInternal() {
        return new DefaultHost.Builder()
                .setCluster(getCluster())
                .setHostname(getName())
                .setMetricsSoftwareState(MetricsSoftwareState.valueOf(getMetricsSoftwareState()))
                .build();
    }
}
// CHECKSTYLE.ON: MemberNameCheck
