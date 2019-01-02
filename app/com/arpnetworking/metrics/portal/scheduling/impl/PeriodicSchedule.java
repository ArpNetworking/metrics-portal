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
package com.arpnetworking.metrics.portal.scheduling.impl;

import models.internal.scheduling.Schedule;

import java.time.Instant;
import java.time.temporal.TemporalAmount;
import javax.annotation.Nullable;

/**
 * Schedule for a job that repeats periodically.
 *
 * @author Spencer Pearson
 */
public final class PeriodicSchedule implements Schedule {

    private final TemporalAmount _period;

    /**
     * @param period The interval over which the job repeats.
     */
    public PeriodicSchedule(final TemporalAmount period) {
        _period = period;
    }

    @Override
    public @Nullable Instant nextRun(final Instant lastRun, final Instant now) {
        Instant result = lastRun.plus(_period);
        while (result.isBefore(now)) {
            result = result.plus(_period);
        }
        return result;
    }

    public TemporalAmount getPeriod() {
        return _period;
    }
}
