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

import javax.annotation.Nullable;
import java.time.Instant;

public interface Schedule {
    // TODO(spencerpearson): should the input be [the last time it ran] or [the current time]?
    //  Argument for [the current time]: we don't need to store the last time each job ran in case the scheduler dies and forgets its plan.
    //  Argument for [the last time it ran]: PeriodicSchedule can't be implemented with just the current time.
    @Nullable Instant nextRun(Instant lastRun);
}
