/*
 * Copyright 2020 Dropbox Inc.
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

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for {@link QueryConsistencyTaskCreator}.
 *
 * @author William Ehlhardt (whale at dropbox dot com)
 */
public class QueryConsistencyTaskCreatorTest {
    @Test
    public void periodStreamForInterval() {
        final List<Instant> actual = QueryConsistencyTaskCreator.periodStreamForInterval(
                Instant.parse("2020-06-11T22:23:21Z"),
                Instant.parse("2020-06-12T01:02:03Z"),
                RollupPeriod.HOURLY).collect(Collectors.toList());

        Assert.assertEquals(Arrays.asList(
                Instant.parse("2020-06-11T22:00:00Z"),
                Instant.parse("2020-06-11T23:00:00Z"),
                Instant.parse("2020-06-12T00:00:00Z"),
                Instant.parse("2020-06-12T01:00:00Z")), actual);
    }
}
