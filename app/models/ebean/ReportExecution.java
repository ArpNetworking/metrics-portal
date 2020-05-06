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

import io.ebean.annotation.DbJsonB;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * An execution event for a {@link ReportExecution}.
 * <p>
 * NOTE: This class is enhanced by Ebean to do things like lazy loading and
 * resolving relationships between beans. Therefore, including functionality
 * which serializes the state of the object could be side-effectful (e.g. {@code toString},
 * {@code @Loggable}, etc.).
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@Table(name = "report_executions", schema = "portal")
@IdClass(ReportExecution.Key.class)
public final class ReportExecution extends BaseExecution<models.internal.reports.Report.Result> {
    @Id
    @ManyToOne(optional = false, targetEntity = Report.class)
    @JoinColumn(name = "report_id")
    private Report report;
    @Nullable
    @DbJsonB
    @Column(name = "result")
    private models.internal.reports.Report.Result result;

    public Report getReport() {
        return report;
    }

    public void setReport(final Report value) {
        report = value;
    }

    @Override
    public models.internal.reports.Report.Result getResult() {
        return result;
    }

    @Override
    public void setResult(@Nullable final models.internal.reports.Report.Result value) {
        result = value;
    }

    @Override
    public UUID getJobId() {
        return report.getUuid();
    }

    @Override
    public void setJobId(final UUID jobId) {
        report.setUuid(jobId);
    }

    /**
     * Primary Key for a {@link ReportExecution}.
     */
    @Embeddable
    protected static final class Key {
        @Nullable
        @Column(name = "report_id")
        private final Long reportId;

        @Nullable
        @Column(name = "scheduled")
        private final Instant scheduled;

        /**
         * Default constructor, required by Ebean.
         */
        public Key() {
            reportId = null;
            scheduled = null;
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
            return Objects.equals(reportId, key.reportId) && Objects.equals(scheduled, key.scheduled);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reportId, scheduled);
        }
    }
}
// CHECKSTYLE.ON: MemberNameCheck
