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

import com.arpnetworking.metrics.portal.scheduling.Schedule;
import org.junit.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link OneOffSchedule}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class OneOffScheduleTest {

    @Test
    public void testNextRun() {
        final Schedule schedule = new OneOffSchedule.Builder()
                .setRunAtAndAfter(Instant.parse("2019-01-01T00:00:00Z"))
                .build();

        // typical progression, from lastRun=null to lastRun>runUntil
        assertEquals(
                Optional.of(Instant.parse("2019-01-01T00:00:00Z")),
                schedule.nextRun(Optional.empty()));
        assertEquals(
                Optional.empty(),
                schedule.nextRun(Optional.of(Instant.parse("2019-01-01T00:00:00Z"))));

        // lastRun < runAfter: next run should be null
        assertEquals(
                Optional.empty(),
                schedule.nextRun(Optional.of(Instant.parse("2018-12-20T12:34:56Z"))));

        // runAfter < lastRun < runUntil: next run should be null
        assertEquals(
                Optional.empty(),
                schedule.nextRun(Optional.of(Instant.parse("2019-01-02T12:34:56Z"))));

        // lastRun > runUntil: next run should be null
        assertEquals(
                Optional.empty(),
                schedule.nextRun(Optional.of(Instant.parse("9999-01-01T00:00:00Z"))));
    }

}
