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

import akka.actor.Actor;
import akka.actor.ActorSystem;
import akka.testkit.TestActorRef;
import akka.testkit.javadsl.TestKit;
import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test cases for {@link ConsistencyChecker}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class QueueActorTest {
    private ActorSystem _system;

    private TestKit _probe;

    private static final AtomicLong SYSTEM_NAME_NONCE = new AtomicLong(0);

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        _system = ActorSystem.create();

        _system = ActorSystem.create(
                "test-" + SYSTEM_NAME_NONCE.getAndIncrement(),
                ConfigFactory.parseMap(AkkaClusteringConfigFactory.generateConfiguration())
        );

        _probe = new TestKit(_system);
    }

    @After
    public void tearDown() {
        TestKit.shutdownActorSystem(_system);
        _system = null;
    }

    @Test
    public void testBasicAddAndPoll() {
        final TestActorRef<Actor> queue = TestActorRef.create(_system, QueueActor.props(Optional.of(2L)));

        queue.tell(new QueueActor.Add<>(1), _probe.getRef());
        _probe.expectMsg(new QueueActor.AddAccepted<>(1));

        queue.tell(new QueueActor.Add<>(2), _probe.getRef());
        _probe.expectMsg(new QueueActor.AddAccepted<>(2));

        queue.tell(new QueueActor.Add<>(3), _probe.getRef());
        _probe.expectMsg(new QueueActor.AddRejected<>(3));

        queue.tell(QueueActor.Poll.getInstance(), _probe.getRef());
        _probe.expectMsg(1);

        queue.tell(QueueActor.Poll.getInstance(), _probe.getRef());
        _probe.expectMsg(2);

        queue.tell(QueueActor.Poll.getInstance(), _probe.getRef());
        _probe.expectMsg(QueueActor.QueueEmpty.getInstance());
    }
}
