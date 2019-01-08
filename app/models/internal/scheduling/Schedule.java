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
package models.internal.scheduling;

import java.time.ZonedDateTime;
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
    Optional<ZonedDateTime> nextRun(Optional<ZonedDateTime> lastRun);
}
