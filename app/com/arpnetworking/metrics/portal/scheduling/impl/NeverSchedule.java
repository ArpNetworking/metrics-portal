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

import com.arpnetworking.logback.annotations.Loggable;
import com.arpnetworking.metrics.portal.scheduling.Schedule;

import java.time.Instant;
import java.util.Optional;

/**
 * Schedule that should never fire.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
@Loggable
public final class NeverSchedule implements Schedule {
    /**
     * The only instance of {@code NeverSchedule}, since they're all identical.
     */
    private static final NeverSchedule INSTANCE = new NeverSchedule();

    private NeverSchedule() {}

    public static NeverSchedule getInstance() {
        return INSTANCE;
    }

    @Override
    public String toString() {
        return "NeverSchedule{}";
    }

    @Override
    public Optional<Instant> nextRun(final Optional<Instant> lastRun) {
        return Optional.empty();
    }

    @Override
    public <T> T accept(final Visitor<T> visitor) {
        return visitor.visitNever(this);
    }
}
