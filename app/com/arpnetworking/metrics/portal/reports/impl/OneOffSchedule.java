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
package com.arpnetworking.metrics.portal.reports.impl;

import com.arpnetworking.metrics.portal.reports.Schedule;

import java.time.Instant;
import javax.annotation.Nullable;

/**
 * Schedule for a {@link com.arpnetworking.metrics.portal.reports.Job} that should be executed exactly once.
 *
 * @author Spencer Pearson
 */
public final class OneOffSchedule implements Schedule {

    /**
     * The only instance of OneOffSchedule. (They all behave the same.)
     */
    public static final OneOffSchedule INSTANCE = new OneOffSchedule();

    private OneOffSchedule() {}

    @Override
    public @Nullable Instant nextRun(final Instant lastRun) {
        return null;
    }

}
