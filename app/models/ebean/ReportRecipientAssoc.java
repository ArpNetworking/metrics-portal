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
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;

@Entity
@Table(name = "reports_to_recipients", schema = "portal")
public class ReportRecipientAssoc {
    @CreatedTimestamp
    @Column(name = "created_at")
    private Timestamp _createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", referencedColumnName = "id")
    private Report _report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", referencedColumnName = "id")
    private Recipient _recipient;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "format_id")
    private ReportFormat _format;

    public Recipient getRecipient() {
        return _recipient;
    }

    public Report getReport() {
        return _report;
    }

    public void setReport(final Report report) {
        _report = report;
    }

    public void setRecipient(final Recipient recipient) {
        _recipient = recipient;
    }

    public void setFormat(final ReportFormat value) {
        _format = value;
    }

    public ReportFormat getFormat() {
        return _format;
    }
}
