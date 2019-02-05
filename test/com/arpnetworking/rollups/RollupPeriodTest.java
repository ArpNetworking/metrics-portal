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
                Instant.parse("2019-01-30T05:00:00Z"),
                RollupPeriod.HOURLY.nextPeriodStart(Instant.parse("2019-01-30T04:20:00Z")));

        assertEquals(
                Instant.parse("2019-01-31T00:00:00Z"),
                RollupPeriod.DAILY.nextPeriodStart(Instant.parse("2019-01-30T04:20:00Z")));
    }

    @Test
    public void testNextTimeMillis() {
        assertEquals(
                Instant.parse("2019-01-30T04:00:00Z"),
                RollupPeriod.HOURLY.nextPeriodStart(Instant.parse("2019-01-30T03:24:00Z")));

        assertEquals(
                Instant.parse("2019-02-01T00:00:00Z"),
                RollupPeriod.DAILY.nextPeriodStart(Instant.parse("2019-01-31T03:24:00Z")));
    }
}
