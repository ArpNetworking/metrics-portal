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
import models.internal.impl.DefaultReportResult;

import java.time.Instant;
import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
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

    /**
     * The state of execution for this particular report job.
     */
    public enum State {
        /**
         * This report execution has been started.
         */
        STARTED,
        /**
         * This report execution completed successfully.
         */
        SUCCESS,
        /**
         * This report execution failed.
         */
        FAILURE,
    }

    public Report getReport() {
        return report;
    }

    public void setReport(final Report value) {
        report = value;
    }

    public State getState() {
        return state;
    }

    public void setState(final State value) {
        state = value;
    }

    @Nullable
    public Instant getStartedAt() {
        return started_at;
    }

    public void setStartedAt(final Instant value) {
        started_at = value;
    }

    public Instant getScheduled() {
        return scheduled;
    }

    public void setScheduled(final Instant value) {
        scheduled = value;
    }

    @Nullable
    public Instant getCompletedAt() {
        return completed_at;
    }

    public void setCompletedAt(final Instant value) {
        completed_at = value;
    }

    @Nullable
    public DefaultReportResult getResult() {
        return result;
    }

    public void setResult(final DefaultReportResult value) {
        result = value;
    }

    @Nullable
    public String getError() {
        return error;
    }

    public void setError(final String value) {
        error = value;
    }

    @Column
    @ManyToOne(optional = false)
    @JoinColumn(name = "report_id")
    private Report report;

    @Column(name = "state")
    @Enumerated(value = EnumType.STRING)
    private State state;

    @Column(name = "scheduled")
    private Instant scheduled;

    @Nullable
    @Column(name = "started_at")
    private Instant started_at;

    @Nullable
    @Column(name = "completed_at")
    private Instant completed_at;

    @Nullable
    @DbJsonB
    @Column(name = "result")
    private DefaultReportResult result;

    @Nullable
    @Column(name = "error")
    private String error;
}
// CHECKSTYLE.ON: MemberNameCheck
