/*
 * Copyright 2019 Dropbox Inc.
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
package com.arpnetworking.rollups;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;

/**
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public class RollupPeriodTest {
    @Test
    public void testRecentEndTimeMillis() {
        assertEquals(
                Instant.from(LocalDateTime.of(2019, 1, 30, 4, 0)),
                RollupPeriod.HOURLY.recentEndTime(Instant.from(LocalDateTime.of(2019, 1, 30, 4, 20))));

        assertEquals(
                Instant.from(LocalDateTime.of(2019, 1, 30, 0, 0)),
                RollupPeriod.DAILY.recentEndTime(Instant.from(LocalDateTime.of(2019, 1, 30, 4, 20))));
    }

    @Test
    public void testNextTimeMillis() {
        assertEquals(
                Instant.from(LocalDateTime.of(2019, 1, 30, 4, 0)),
                RollupPeriod.HOURLY.nextPeriodStart(Instant.from(LocalDateTime.of(2019, 1, 30, 3, 24))));

        assertEquals(
                Instant.from(LocalDateTime.of(2019, 2, 1, 0, 0)),
                RollupPeriod.DAILY.nextPeriodStart(Instant.from(LocalDateTime.of(2019, 1, 31, 3, 24))));
    }
}
