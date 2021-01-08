/*
 * Copyright 2021 Dropbox, Inc.
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

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * Tests for {@link CacheActor}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class CacheActorTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private ActorRef _cache;
    private ActorSystem _actorSystem;
    private PeriodicMetrics _metrics;

    @Before
    public void setUp() {
        _metrics = Mockito.mock(PeriodicMetrics.class);
        _actorSystem = ActorSystem.create("TestCacheSystem");
        _cache = _actorSystem.actorOf(CacheActor.props("testCacheName", _metrics));
    }

    @After
    public void tearDown() {
        TestKit.shutdownActorSystem(_actorSystem);
    }

    @Test
    public void testCache() throws Exception {
        final Optional<Integer> noValue = CacheActor.<String, Integer>get(_cache, "foo", TIMEOUT).toCompletableFuture().get();
        assertThat(noValue, is(Optional.empty()));

        CacheActor.put(_cache, "foo", 42, TIMEOUT).toCompletableFuture().get();
        final Optional<Integer> fooValue = CacheActor.<String, Integer>get(_cache, "foo", TIMEOUT).toCompletableFuture().get();
        assertThat(fooValue, is(Optional.of(42)));

        CacheActor.put(_cache, "bar", 123, TIMEOUT).toCompletableFuture().get();
        Optional<Integer> barValue = CacheActor.<String, Integer>get(_cache, "bar", TIMEOUT).toCompletableFuture().get();
        assertThat(barValue, is(Optional.of(123)));

        CacheActor.put(_cache, "bar", 456, TIMEOUT).toCompletableFuture().get();
        barValue = CacheActor.<String, Integer>get(_cache, "bar", TIMEOUT).toCompletableFuture().get();
        assertThat(barValue, is(Optional.of(456)));

        final Optional<Integer> missingValue =
                CacheActor.<String, Integer>get(_cache, "missing-key", TIMEOUT)
                    .toCompletableFuture().get();
        assertThat(missingValue, is(Optional.empty()));
    }
}
