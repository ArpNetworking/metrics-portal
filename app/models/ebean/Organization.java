/*
 * Copyright 2016 Smartsheet.com
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

import io.ebean.Database;
import io.ebean.annotation.WhenCreated;
import io.ebean.annotation.WhenModified;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/**
 * Data model for organizations.
 *
 * NOTE: This class is enhanced by Ebean to do things like lazy loading and
 * resolving relationships between beans. Therefore, including functionality
 * which serializes the state of the object can be dangerous (e.g. {@code toString},
 * {@code @Loggable}, etc.).
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@Table(name = "organizations", schema = "portal")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid")
    private UUID uuid;

    @Version
    @Column(name = "version")
    private Long version;

    @WhenCreated
    @Column(name = "created_at")
    private Timestamp createdAt;

    @WhenModified
    @Column(name = "updated_at")
    private Timestamp updatedAt;

    /**
     * Finds an {@link Organization} when given an {@link models.internal.Organization}.
     *
     * @param ebeanServer The {@code EbeanServer} instance to use.
     * @param organization The organization to lookup.
     * @return The organization from the database.
     */
    public static Optional<Organization> findByOrganization(
            final Database ebeanServer,
            final models.internal.Organization organization) {
        return Optional.ofNullable(
                ebeanServer.createQuery(Organization.class)
                        .where()
                        .eq("uuid", organization.getId())
                        .findOne());
    }

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
        this.uuid = value;
    }
}
// CHECKSTYLE.ON: MemberNameCheck
