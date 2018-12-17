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

import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * Data Model for SQL storage of a report recipient.
 *
 * @see ReportRecipient.RecipientType
 * @author Christian Briones (cbriones at dropbox dot com)
 */
// CHECKSTYLE.OFF: FinalClassCheck - Ebean requires the class to be non-final.
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@Table(name = "report_recipients", schema = "portal")
public class ReportRecipient {
    /**
     * The type of report recipient.
     */
    public enum RecipientType {
        /**
         * An email address.
         */
        EMAIL
    }

    private ReportRecipient(final RecipientType typeValue, final String recipientValue) {
        type = typeValue;
        recipient = recipientValue;
    }

    /**
     * Create a new ReportRecipient with the given emailAddress.
     * @param emailAddress The address of the recipient
     * @return A new email recipient.
     */
    public static ReportRecipient newEmailRecipient(final String emailAddress) {
        return new ReportRecipient(RecipientType.EMAIL, emailAddress);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "recipient_group_id")
    private ReportRecipientGroup recipientGroup;

    @Column(name = "recipient")
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private RecipientType type;

    public Long getId() {
        return id;
    }

    /**
     * Get the address of this recipient.
     * @return The address of the recipient.
     */
    public String get() {
        return recipient;
    }

    public RecipientType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "ReportRecipient{"
                + "id=" + id
                + ", recipient='" + recipient + '\''
                + ", group.uuid='" + recipientGroup.getUuid() + '\''
                + ", type=" + type
                + '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ReportRecipient that = (ReportRecipient) o;
        return Objects.equals(id, that.id)
                && Objects.equals(recipientGroup, that.recipientGroup)
                && Objects.equals(recipient, that.recipient)
                && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, recipientGroup, recipient, type);
    }
}
// CHECKSTYLE.ON: MemberNameCheck
// CHECKSTYLE.ON: FinalClassCheck
