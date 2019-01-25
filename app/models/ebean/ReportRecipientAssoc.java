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

import io.ebean.annotation.CreatedTimestamp;

import java.sql.Timestamp;
import java.util.Objects;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;

@Entity
@Table(name = "reports_to_recipients", schema = "portal")
@IdClass(ReportRecipientAssoc.PK.class)
// CHECKSTYLE.OFF: MemberNameCheck
public class ReportRecipientAssoc {
    @CreatedTimestamp
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

    public Report getReport() {
        return report;
    }

    public void setReport(final Report value) {
        report = value;
    }

    public void setRecipient(final Recipient value) {
        recipient = value;
    }

    public void setFormat(final ReportFormat value) {
        format = value;
    }

    public ReportFormat getFormat() {
        return format;
    }

    @Embeddable
    public static class PK {
        @Column(name = "report_id")
        public Long report;

        @Column(name = "recipient_id")
        public Long recipient;

        /**
         * Default constructor, needed by Ebean.
         */
        public PK() {
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
            final PK pk = (PK) o;
            return Objects.equals(report, pk.report) && Objects.equals(recipient, pk.recipient);
        }

        @Override
        public int hashCode() {
            return Objects.hash(report, recipient);
        }
    }
}
// CHECKSTYLE.ON: MemberNameCheck
