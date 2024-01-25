/*
 * Copyright 2019 Dropbox, Inc.
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

import java.sql.Timestamp;
import java.util.Objects;
import javax.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Data model for a mapping between a report and a recipient.
 *
 * NOTE: This class is enhanced by Ebean to do things like lazy loading and
 * resolving relationships between beans. Therefore, including functionality
 * which serializes the state of the object can be dangerous (e.g. {@code toString},
 * {@code @Loggable}, etc.).
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@Entity
@Table(name = "reports_to_recipients", schema = "portal")
@IdClass(ReportRecipientAssoc.Key.class)
// CHECKSTYLE.OFF: MemberNameCheck
public class ReportRecipientAssoc {
    @WhenCreated
    @Column(name = "created_at")
    private Timestamp createdAt;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", referencedColumnName = "id")
    private Report report;

    @Id
    @ManyToOne(fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.REFRESH})
    @JoinColumn(name = "recipient_id", referencedColumnName = "id")
    private Recipient recipient;

    @OneToOne(cascade = CascadeType.PERSIST, orphanRemoval = true)
    @JoinColumn(name = "format_id")
    private ReportFormat format;

    public Recipient getRecipient() {
        return recipient;
    }

    public void setRecipient(final Recipient value) {
        recipient = value;
    }

    public Report getReport() {
        return report;
    }

    public void setReport(final Report value) {
        report = value;
    }

    public ReportFormat getFormat() {
        return format;
    }

    public void setFormat(final ReportFormat value) {
        format = value;
    }

    /**
     * Primary key for a {@link ReportRecipientAssoc}.
     */
    @Embeddable
    protected static final class Key {
        @Nullable
        @Column(name = "report_id")
        private Long report;

        @Nullable
        @Column(name = "recipient_id")
        private Long recipient;

        /**
         * Default constructor, needed by Ebean.
         */
        public Key() {
            report = null;
            recipient = null;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Key key = (Key) o;
            return Objects.equals(report, key.report) && Objects.equals(recipient, key.recipient);
        }

        @Override
        public int hashCode() {
            return Objects.hash(report, recipient);
        }
    }
}
// CHECKSTYLE.ON: MemberNameCheck
