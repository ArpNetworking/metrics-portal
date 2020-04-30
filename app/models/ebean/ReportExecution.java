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

import com.google.common.base.MoreObjects;
import io.ebean.annotation.DbJsonB;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
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
public final class ReportExecution {
    private static final String EXCEPTION_KEY = "exception";

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "report_id")
    private Report report;
    @Column(name = "state")
    @Enumerated(value = EnumType.STRING)
    private State state;
    @Id
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
     * @return The error message encoded as a string.
     */
    @Nullable
    public String getError() {
        return error == null ? null : error.get(EXCEPTION_KEY);
    }

    /**
     * Set the error message associated with this execution.
     *
     * @param value the error
     */
    public void setError(final String value) {
        error = Collections.singletonMap(EXCEPTION_KEY, value);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("report", report)
                .add("state", state)
                .add("scheduled", scheduled)
                .add("started_at", started_at)
                .add("completed_at", completed_at)
                .add("result", result)
                .add("error", error)
                .toString();
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
     * Primary Key for a {@link ReportExecution}.
     */
    @Embeddable
    protected static final class Key {
        @Nullable
        @Column(name = "report_id")
        private Long reportId;

        @Nullable
        @Column(name = "scheduled")
        private Instant scheduled;

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
