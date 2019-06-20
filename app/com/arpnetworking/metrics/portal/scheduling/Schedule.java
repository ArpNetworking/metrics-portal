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
package com.arpnetworking.metrics.portal.scheduling;

import com.arpnetworking.metrics.portal.scheduling.impl.NeverSchedule;
import com.arpnetworking.metrics.portal.scheduling.impl.OneOffSchedule;
import com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule;

import java.time.Instant;
import java.util.Optional;

/**
 * A schedule on which to run a job (e.g. "once", "daily", "weekly", "on the first of each month").
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public interface Schedule {
    /**
     * Determines when next to run the job.
     *
     * @param lastRun The last time the job was run.
     * @return The next time to run the job.
     */
    Optional<Instant> nextRun(Optional<Instant> lastRun);

    /**
     * Applies a {@code Visitor} to this schedule. This should delegate the to the appropriate {@code Visitor#visit} overload.
     *
     * @param visitor the visitor
     * @param <T> the return type of the visitor. Use {@link Void} for visitors that do not need to return a result.
     * @return The result of applying the visitor.
     */
    <T> T accept(Visitor<T> visitor);

    /**
     * {@code Visitor} abstracts over operations which could potentially handle various
     * implementations of ReportFormat.
     *
     * @param <T> the return type of the visitor.
     */
    abstract class Visitor<T> {
        /**
         * Visit a {@link PeriodicSchedule}.
         *
         * @param schedule The schedule to visit.
         * @return The result of applying the visitor.
         */
        public abstract T visitPeriodic(PeriodicSchedule schedule);

        /**
         * Visit a {@link OneOffSchedule}.
         *
         * @param schedule The schedule to visit.
         * @return The result of applying the visitor.
         */
        public abstract T visitOneOff(OneOffSchedule schedule);

        /**
         * Visit a {@link NeverSchedule}.
         *
         * @param schedule The schedule to visit.
         * @return The result of applying the visitor.
         */
        public abstract T visitNever(NeverSchedule schedule);
    }
}
