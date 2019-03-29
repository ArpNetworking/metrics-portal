/*
 * Copyright 2018 Smartsheet.com
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
package com.arpnetworking.metrics.portal.query;

import models.internal.impl.DefaultAlertTrigger;
import net.sf.oval.exception.ConstraintsViolatedException;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Test;

import java.util.Optional;

/**
 * Tests for the {@link DefaultAlertTrigger} class.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
 */
public class DefaultAlertTriggerTest {

    @Test
    public void testEmptyEndDate() {
        final DefaultAlertTrigger trigger = new DefaultAlertTrigger.Builder()
                .setTime(DateTime.now())
                .setEndTime(Optional.empty())
                .build();
        Assert.assertNotNull(trigger);
    }

    @Test(expected = ConstraintsViolatedException.class)
    public void testNullEndDate() {
        final DefaultAlertTrigger trigger = new DefaultAlertTrigger.Builder()
                .setTime(DateTime.now())
                .setEndTime(null)
                .build();
        Assert.assertNotNull(trigger);
    }

    @Test(expected = ConstraintsViolatedException.class)
    public void testEndBeforeStart() {
        final DefaultAlertTrigger trigger = new DefaultAlertTrigger.Builder()
                .setTime(DateTime.now())
                .setEndTime(Optional.of(DateTime.now().minusMinutes(5)))
                .build();
        Assert.assertNotNull(trigger);
    }
}
