/*
 * Copyright 2020 Dropbox, Inc.
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
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;
import com.arpnetworking.commons.java.time.ManualClock;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.ebean.EbeanServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;
import javax.persistence.PersistenceException;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Tests for {@link DailyPartitionCreator}.
 *
 * These don't actually exercise the DB code, but that case should be covered
 * instead by the integration tests that use this class.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class DailyPartitionCreatorTest {
    private static final Object EXECUTE_WAS_CALLED = new Object();
    private static final Duration MSG_TIMEOUT = Duration.ofSeconds(1);
    private static final long TEST_LOOKAHEAD = 7;

    private ManualClock _clock;

    // Unused Mocks
    private EbeanServer _server;
    private PeriodicMetrics _metrics;

    // ActorSystem fields
    private ActorSystem _actorSystem;
    private TestKit _probe;

    @Before
    public void setUp() {
        _server = Mockito.mock(EbeanServer.class);
        _metrics = Mockito.mock(PeriodicMetrics.class);
        _clock = new ManualClock(Instant.now(), Duration.ofDays(1), ZoneOffset.UTC);

        _actorSystem = ActorSystem.create();
        _probe = new TestKit(_actorSystem);
    }

    private ActorRef createActor() {
        return createActor(() -> _probe.getRef().tell(EXECUTE_WAS_CALLED, _probe.getRef()));
    }

    @SuppressFBWarnings("SIC_INNER_SHOULD_BE_STATIC_ANON")
    private ActorRef createActor(final Runnable executeFn) {
        final Props props = Props.create(
                DailyPartitionCreator.class,
                () -> new DailyPartitionCreator(
                        _server,
                        _metrics,
                        "testSchema",
                        "testTable",
                        Duration.ZERO,
                        (int) TEST_LOOKAHEAD,
                        _clock
                ) {
                    @Override
                    protected void execute(
                            final String schema,
                            final String table,
                            final LocalDate startDate,
                            final LocalDate endDate
                    ) {
                        executeFn.run();

                        final long difference = ChronoUnit.DAYS.between(startDate, endDate);
                        assertThat("range should respect lookahead", difference, equalTo(TEST_LOOKAHEAD));

                        final long clockDifference = ChronoUnit.DAYS.between(
                                startDate,
                                ZonedDateTime.ofInstant(_clock.instant(), _clock.getZone())
                        );
                        assertThat("range should start from current date", clockDifference, equalTo(0L));
                    }
                }
        );
        final ActorRef ref = _actorSystem.actorOf(props);
        _probe.watch(ref);
        return ref;
    }

    @After
    public void tearDown() {
        TestKit.shutdownActorSystem(_actorSystem);
    }

    @Test
    public void testCreatePartitionsOnTick() throws Exception {
        final ActorRef ref = createActor();

        // First call should have been synchronous
        DailyPartitionCreator.start(ref, MSG_TIMEOUT);
        _probe.expectMsg(EXECUTE_WAS_CALLED);

        for (int i = 0; i < 3; i++) {
            // Clock didn't move
            ref.tell(DailyPartitionCreator.TICK, _probe.getRef());
            _probe.expectNoMessage(MSG_TIMEOUT);

            // Clock moved 1 day
            _clock.tick();
            ref.tell(DailyPartitionCreator.TICK, _probe.getRef());
            _probe.expectMsg(EXECUTE_WAS_CALLED);
        }
        DailyPartitionCreator.stop(ref, MSG_TIMEOUT);
        _probe.expectTerminated(ref);
    }

    @Test(expected = ExecutionException.class)
    public void testDoubleStartWithoutStop() throws Exception {
        final ActorRef ref = createActor();

        DailyPartitionCreator.start(ref, MSG_TIMEOUT);
        DailyPartitionCreator.start(ref, MSG_TIMEOUT);
    }

    @Test(expected = ExecutionException.class)
    public void testExecutionError() throws Exception {
        final ActorRef ref = createActor(
                () -> {
                    throw new PersistenceException("Something went wrong");
                }
        );
        DailyPartitionCreator.start(ref, MSG_TIMEOUT);
    }
}