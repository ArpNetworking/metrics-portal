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

import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.arpnetworking.commons.test.BuildableTestHelper;
import com.arpnetworking.commons.test.EqualityTestHelper;
import com.arpnetworking.commons.test.ThreadLocalBuildableTestHelper;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * Test cases for {@link RollupMetric}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class RollupMetricTest {

    @Test
    public void testParse() {
        assertEquals(
                Optional.of(ThreadLocalBuilder.build(RollupMetric.Builder.class, b -> b
                                .setBaseMetricName("foo")
                                .setPeriod(RollupPeriod.HOURLY))
                ),
                RollupMetric.fromRollupMetricName("foo_1h")
        );

        assertEquals(
                Optional.of(ThreadLocalBuilder.build(RollupMetric.Builder.class, b -> b
                        .setBaseMetricName("foo")
                        .setPeriod(RollupPeriod.DAILY))
                ),
                RollupMetric.fromRollupMetricName("foo_1d")
        );

        assertEquals(
                Optional.empty(),
                RollupMetric.fromRollupMetricName("foo_1x")
        );
    }

    @Test
    public void testGetRollupName() {
        assertEquals(
                "foo_1h",
                ThreadLocalBuilder.build(RollupMetric.Builder.class, b -> b
                        .setBaseMetricName("foo")
                        .setPeriod(RollupPeriod.HOURLY)
                ).getRollupMetricName()
        );
    }

    @Test
    public void testBuild() throws Exception {
        BuildableTestHelper.testBuild(
                new RollupMetric.Builder()
                        .setBaseMetricName("foo")
                        .setPeriod(RollupPeriod.HOURLY),
                RollupMetric.class
        );
    }

    @Test
    public void testBuilderReset() throws Exception {
        ThreadLocalBuildableTestHelper.testReset(
                new RollupMetric.Builder()
                        .setBaseMetricName("foo")
                        .setPeriod(RollupPeriod.HOURLY)
        );
    }

    @Test
    public void testEquality() throws Exception {
        EqualityTestHelper.testEquality(
                new RollupMetric.Builder()
                        .setBaseMetricName("foo")
                        .setPeriod(RollupPeriod.HOURLY),
                new RollupMetric.Builder()
                        .setBaseMetricName("bar")
                        .setPeriod(RollupPeriod.DAILY),
                RollupMetric.class
        );
    }
}
