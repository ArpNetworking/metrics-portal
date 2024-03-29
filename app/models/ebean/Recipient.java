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

import com.arpnetworking.metrics.portal.reports.RecipientType;
import io.ebean.annotation.WhenCreated;
import io.ebean.annotation.WhenModified;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import models.internal.impl.DefaultRecipient;

import java.sql.Timestamp;
import java.util.Objects;
import java.util.UUID;

/**
 * Data Model for SQL storage of a report recipient.
 *
 * NOTE: This class is enhanced by Ebean to do things like lazy loading and
 * resolving relationships between beans. Therefore, including functionality
 * which serializes the state of the object can be dangerous (e.g. {@code toString},
 * {@code @Loggable}, etc.).
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 * @see RecipientType
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@Table(name = "recipients", schema = "portal")
public final class Recipient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid")
    private UUID uuid;

    @WhenCreated
    @Column(name = "created_at")
    private Timestamp createdAt;

    @WhenModified
    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @Column(name = "address")
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private RecipientType type;

    private Recipient(final RecipientType typeValue, final String addressValue) {
        type = typeValue;
        address = addressValue;
    }

    /**
     * Create a new Recipient with the given address.
     *
     * @param type The {@link RecipientType} of the recipient
     * @param address The address of the recipient
     * @return A new email recipient.
     */
    public static Recipient newRecipient(final RecipientType type, final String address) {
        return new Recipient(type, address);
    }

    public void setUuid(final UUID value) {
        uuid = value;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setId(final Long value) {
        id = value;
    }

    public Long getId() {
        return id;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Get the address of this recipient.
     *
     * @return The address of the recipient.
     */
    public String get() {
        return address;
    }

    public RecipientType getType() {
        return type;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !getClass().equals(o.getClass())) {
            return false;
        }
        final Recipient that = (Recipient) o;
        return Objects.equals(id, that.id)
                && Objects.equals(uuid, that.uuid)
                && Objects.equals(address, that.address)
                && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, uuid, address, type);
    }


    /* package */ models.internal.reports.Recipient toInternal() {
        return new DefaultRecipient.Builder()
                .setId(uuid)
                .setType(type)
                .setAddress(address)
                .build();
    }
}
// CHECKSTYLE.ON: MemberNameCheck
