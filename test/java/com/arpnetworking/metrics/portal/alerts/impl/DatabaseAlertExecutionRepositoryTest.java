/*
 * Copyright 2023 Brandon Arp
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
package com.arpnetworking.metrics.portal.alerts.impl;

import net.sf.oval.exception.ConstraintsViolatedException;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link DatabaseAlertExecutionRepository}.
 *
 * @author Brandon Arp (brandon arp at gmail dot com)
 */
public class DatabaseAlertExecutionRepositoryTest {
    @Test
    public void testPartitionManagerBuilder() {
        DatabaseAlertExecutionRepository.PartitionManager pm;
        pm = new DatabaseAlertExecutionRepository.PartitionManager.Builder()
                .build();
        Assert.assertNotNull(pm);

        pm = new DatabaseAlertExecutionRepository.PartitionManager.Builder()
                .setLookahead(7)
                .build();
        Assert.assertNotNull(pm);

        pm = new DatabaseAlertExecutionRepository.PartitionManager.Builder()
                .setLookahead(7)
                .setRetainCount(30)
                .build();
        Assert.assertNotNull(pm);

        try {
            pm = new DatabaseAlertExecutionRepository.PartitionManager.Builder()
                    .setLookahead(7)
                    .setRetainCount(3)
                    .build();
            Assert.fail("Expected exception");
        } catch (final ConstraintsViolatedException ignored) {
        }
    }

}
