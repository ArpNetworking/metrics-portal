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
import io.ebean.annotation.UpdatedTimestamp;
import models.internal.impl.DefaultEmailRecipient;

import java.sql.Timestamp;
import java.util.Objects;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PersistenceException;
import javax.persistence.Table;

/**
 * Data Model for SQL storage of a report recipient.
 *
 * NOTE: This class is enhanced by Ebean to do things like lazy loading and
 * resolving relationships between beans. Therefore, including functionality
 * which serializes the state of the object can be dangerous (e.g. {@code toString},
 * {@code @Loggable}, etc.).
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 * @see Recipient.RecipientType
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

    @CreatedTimestamp
    @Column(name = "created_at")
    private Timestamp createdAt;

    @UpdatedTimestamp
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
     * Create a new Recipient with the given emailAddress.
     *
     * @param emailAddress The address of the recipient
     * @return A new email recipient.
     */
    public static Recipient newEmailRecipient(final String emailAddress) {
        return new Recipient(RecipientType.EMAIL, emailAddress);
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

    /**
     * The type of report recipient.
     */
    public enum RecipientType {
        /**
         * An email address.
         */
        EMAIL
    }

    /* package */ models.internal.reports.Recipient toInternal() {
        if (type == RecipientType.EMAIL) {
            return new DefaultEmailRecipient.Builder()
                    .setId(uuid)
                    .setAddress(address)
                    .build();
        }
        throw new PersistenceException("recipient type does not have an internal representation: " + type);
    }
}
// CHECKSTYLE.ON: MemberNameCheck
