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

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * An execution event for a {@link ReportingJob}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@Table(name = "reporting_job_executions", schema = "portal")
public class ReportingJobExecution {
    public ReportingJob getJob() {
        return job;
    }

    public void setJob(final ReportingJob value) {
        job = value;
    }

    public ReportingJob.Result getResult() {
        return result;
    }

    public void setResult(final ReportingJob.Result value) {
        result = value;
    }

    public ZonedDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(final ZonedDateTime value) {
        executedAt = value;
    }

    @Id
    private Long id;

    @Column
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "reporting_job_id")
    private ReportingJob job;

    @Column(name = "result")
    @Enumerated(value = EnumType.STRING)
    private ReportingJob.Result result;

    @Column(name = "executed_at")
    private ZonedDateTime executedAt;
}
// CHECKSTYLE.ON: MemberNameCheck
