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

import com.google.common.base.Throwables;
import io.ebean.annotation.DbJsonB;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
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
public final class ReportExecution {

    private static final String EXCEPTION_KEY = "exception";

    @EmbeddedId
    private ReportExecutionKey key;
    @Column
    @ManyToOne(optional = false)
    @JoinColumn(name = "report_id")
    private Report report;
    @Column(name = "state")
    @Enumerated(value = EnumType.STRING)
    private State state;
    @Column(name = "scheduled", insertable = false, updatable = false)
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
    private models.internal.reports.Report.Result result;
    @Nullable
    @DbJsonB
    @Column(name = "error")
    private Map<String, String> error;

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

    public void setCompletedAt(@Nullable final Instant value) {
        completed_at = value;
    }

    @Nullable
    public models.internal.reports.Report.Result getResult() {
        return result;
    }

    public void setResult(@Nullable final models.internal.reports.Report.Result value) {
        result = value;
    }

    /**
     * Get the error associated with this execution, if any.
     *
     * @return The error encoded as a string.
     */
    @Nullable
    public String getError() {
        return error == null ? null : error.get(EXCEPTION_KEY);
    }

    /**
     * Set the error associated with this execution.
     *
     * @param value the error
     */
    public void setError(@Nullable final Throwable value) {
        if (value == null) {
            error = null;
            return;
        }
        error = Collections.singletonMap(EXCEPTION_KEY, Throwables.getStackTraceAsString(value));
    }

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

    /**
     * Composite Key used by Ebean.
     */
    @Embeddable
    protected static final class ReportExecutionKey {
        @Nullable
        @Column(name = "report_id")
        private Long _reportId;

        @Nullable
        @Column(name = "scheduled")
        private Instant _scheduled;

        ReportExecutionKey(@Nullable final Long reportId, @Nullable final Instant scheduled) {
            _reportId = reportId;
            _scheduled = scheduled;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ReportExecutionKey key = (ReportExecutionKey) o;
            return Objects.equals(_reportId, key._reportId) && Objects.equals(_scheduled, key._scheduled);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_reportId, _scheduled);
        }
    }
}
// CHECKSTYLE.ON: MemberNameCheck
