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
package com.arpnetworking.metrics.portal.health;

import com.arpnetworking.notcommons.pekko.BaseActorTest;
import com.arpnetworking.utility.test.SimpleReplierActor;
import org.apache.pekko.actor.ActorRef;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the {@link StatusActor} actor.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
public class PekkoMembershipHealthProviderTest extends BaseActorTest {

    @Before
    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void recognizesUnhealthy() {
        final ActorRef unhealthyActor = getSystem().actorOf(SimpleReplierActor.props(false));
        final PekkoMembershipHealthProvider provider = new PekkoMembershipHealthProvider(unhealthyActor);
        final boolean isHealthy = provider.isHealthy();
        assertFalse(isHealthy);
    }

    @Test
    public void recognizesHealthy() {
        final ActorRef healthyActor = getSystem().actorOf(SimpleReplierActor.props(true));
        final PekkoMembershipHealthProvider provider = new PekkoMembershipHealthProvider(healthyActor);
        final boolean isHealthy = provider.isHealthy();
        assertTrue(isHealthy);
    }
}

