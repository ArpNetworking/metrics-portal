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

import com.arpnetworking.kairos.client.KairosDbRequestException;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test cases for {@link RollupPartitioner}.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public final class RollupPartitionerTest {
    @Test
    public void testPartitioning() throws Exception {
        final RollupPartitioner partitioner = new RollupPartitioner();
        RollupDefinition job = TestBeanFactory.createRollupDefinitionBuilder()
                .setAllMetricTags(ImmutableMultimap.of(
                        "twoValues", "1/2", "twoValues", "2/2",
                        "threeValues", "1/3", "threeValues", "2/3", "threeValues", "3/3"
                ))
                .setFilterTags(ImmutableMap.of())
                .build();
        assertEquals(
                ImmutableSet.of(
                        RollupDefinition.Builder.<RollupDefinition, RollupDefinition.Builder>clone(job)
                                .setFilterTags(ImmutableMap.of("threeValues", "1/3"))
                                .build(),
                        RollupDefinition.Builder.<RollupDefinition, RollupDefinition.Builder>clone(job)
                                .setFilterTags(ImmutableMap.of("threeValues", "2/3"))
                                .build(),
                        RollupDefinition.Builder.<RollupDefinition, RollupDefinition.Builder>clone(job)
                                .setFilterTags(ImmutableMap.of("threeValues", "3/3"))
                                .build()
                ),
                partitioner.splitJob(job)
        );

        job = TestBeanFactory.createRollupDefinitionBuilder()
                .setAllMetricTags(ImmutableMultimap.of(
                        "oneValue", "1/1"
                ))
                .setFilterTags(ImmutableMap.of())
                .build();
        try {
            partitioner.splitJob(job);
            fail("should have been unable to split job");
        } catch (final RollupPartitioner.CannotSplitException err) {
        }
    }

    @Test
    public void testRetryabilityChecking() {
        final RollupPartitioner partitioner = new RollupPartitioner();
        assertFalse(partitioner.mightSplittingFixFailure(new RuntimeException()));
        assertFalse(partitioner.mightSplittingFixFailure(new KairosDbRequestException(
                400,
                "some error message",
                URI.create("http://kairos"),
                Duration.ofMinutes(2)
        )));
        assertTrue(partitioner.mightSplittingFixFailure(new KairosDbRequestException(
                500,
                "some error message",
                URI.create("http://kairos"),
                Duration.ofMinutes(2)
        )));
        assertFalse(partitioner.mightSplittingFixFailure(new KairosDbRequestException(
                500,
                "some error message",
                URI.create("http://kairos"),
                Duration.ofSeconds(1)
        )));
    }
}
