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

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * An execution event for a {@link Report}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@Table(name = "report_executions", schema = "portal")
public class ReportExecution {
    public Report getReport() {
        return report;
    }

    public void setReport(final Report value) {
        report = value;
    }

    public Report.State getState() {
        return state;
    }

    public void setState(final Report.State value) {
        state = value;
    }

    public Instant getStartedAt() {
        return started_at;
    }

    public void setStartedAt(final Instant value) {
        started_at = value;
    }

    // FIXME(cbriones): Is there a ByteString I can use here instead?
    public byte[] getResult() {
        return result;
    }

    public void setResult(final byte[] value) {
        result = value;
    }

    @Id
    private Long id;

    @Column
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private Report report;

    @Column(name = "state")
    @Enumerated(value = EnumType.STRING)
    private Report.State state;

    @Column(name = "scheduled_for")
    private Instant scheduled_for;

    @Column(name = "started_at")
    private Instant started_at;

    @Column(name = "completed_at")
    private Instant completed_at;

    @Lob
    @Column(name = "result")
    private byte[] result;
}
// CHECKSTYLE.ON: MemberNameCheck
