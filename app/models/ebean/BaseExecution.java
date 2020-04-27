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
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;

/**
 * A execution event for a {@link models.internal.Alert}.
 * <p>
 * NOTE: This class is enhanced by Ebean to do things like lazy loading and
 * resolving relationships between beans. Therefore, including functionality
 * which serializes the state of the object can be dangerous (e.g. {@code toString},
 * {@code @Loggable}, etc.).
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@MappedSuperclass
public abstract class BaseExecution<T> {
    private static final String EXCEPTION_KEY = "exception";

    @Id
    @Column(name = "scheduled")
    protected Instant scheduled;
    @Column(name = "state")
    @Enumerated(value = EnumType.STRING)
    private State state;
    @Nullable
    @Column(name = "started_at")
    private Instant started_at;
    @Nullable
    @Column(name = "completed_at")
    private Instant completed_at;
    @Nullable
    @DbJsonB
    @Column(name = "error")
    private Map<String, String> error;

    /**
     * Get the corresponding Job ID for this execution.
     *
     * @return The job ID
     */
    public abstract UUID getJobId();

    /**
     * Set the corresponding Job ID for this execution.
     *
     * @param jobId The job ID
     */
    public abstract void setJobId(UUID jobId);

    /**
     * Get the result of this execution, if any.
     *
     * @return The result, or null if this execution has not completed.
     */
    public abstract @Nullable T getResult();

    /**
     * Set the result for this execution.
     *
     * @param value the result
     */
    public abstract void setResult(@Nullable T value);

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
    public void setError(@Nullable final String value) {
        if (value == null) {
            error = null;
            return;
        }
        error = Collections.singletonMap(EXCEPTION_KEY, value);
    }

    /**
     * The state of execution for this particular alert job.
     */
    public enum State {
        /**
         * This alert execution has been started.
         */
        STARTED,
        /**
         * This alert execution completed successfully.
         */
        SUCCESS,
        /**
         * This alert execution failed.
         */
        FAILURE,
    }
}
// CHECKSTYLE.ON: MemberNameCheck
