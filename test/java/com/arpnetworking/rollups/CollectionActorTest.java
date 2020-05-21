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

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Status;
import akka.pattern.Patterns;
import akka.testkit.TestActorRef;
import akka.testkit.javadsl.TestKit;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test cases for {@link ConsistencyChecker}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class CollectionActorTest {
    @Mock
    private PeriodicMetrics _periodicMetrics;

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
        final ActorRef actor = TestActorRef.create(_system, CollectionActor.props(
                Optional.of(2L),
                Sets.newHashSet(),
                _periodicMetrics,
                ""
        ));

        actor.tell(new CollectionActor.Add<>(1), _probe.getRef());
        _probe.expectMsgClass(Status.Success.class);

        actor.tell(new CollectionActor.Add<>(2), _probe.getRef());
        _probe.expectMsgClass(Status.Success.class);

        actor.tell(new CollectionActor.Add<>(3), _probe.getRef());
        _probe.expectMsgClass(Status.Failure.class);

        actor.tell(CollectionActor.Poll.getInstance(), _probe.getRef());
        actor.tell(CollectionActor.Poll.getInstance(), _probe.getRef());
        _probe.expectMsgAllOf(ImmutableSet.of(
                new Status.Success(1),
                new Status.Success(2)
        ).toArray());

        actor.tell(CollectionActor.Poll.getInstance(), _probe.getRef());
        _probe.expectMsgClass(Status.Failure.class);
    }

    @Test
    public void testAsk() throws Exception {
        final ActorRef actor = TestActorRef.create(_system, CollectionActor.props(
                Optional.of(1L),
                Sets.newHashSet(),
                _periodicMetrics,
                ""
        ));

        assertEquals(
                1,
                Patterns.ask(actor, new CollectionActor.Add<>(1), Duration.ofSeconds(1)).toCompletableFuture().get()
        );

        Patterns.ask(actor, new CollectionActor.Add<>(1), Duration.ofSeconds(1))
                .handle((response, failure) -> {
                    assertTrue(failure instanceof CollectionActor.Full);
                    return null;
                })
                .toCompletableFuture()
                .get();
    }
}
