/*
 * Copyright 2018 Dropbox, Inc.
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
package com.arpnetworking.metrics.portal.reports;

import com.google.common.base.MoreObjects;

import java.time.Instant;
import java.util.Objects;

/**
 * A specification of some job to run at a future date.
 *
 * @author Spencer Pearson
 */
public final class ScheduledJob {
    private final Instant _whenRun;
    private final String _jobId;

    /**
     * @param whenRun When the job should be executed.
     * @param jobId The id of the job to be executed, assigned by some {@link JobRepository}.
     */
    public ScheduledJob(final Instant whenRun, final String jobId) {
        _whenRun = whenRun;
        _jobId = jobId;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("whenRun", _whenRun)
                .add("jobId", _jobId)
                .toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ScheduledJob that = (ScheduledJob) o;
        return Objects.equals(_whenRun, that._whenRun)
                && Objects.equals(_jobId, that._jobId);
    }

    @Override
    public int hashCode() {

        return Objects.hash(_whenRun, _jobId);
    }

    public Instant getWhenRun() {
        return _whenRun;
    }

    public String getJobId() {
        return _jobId;
    }
}
