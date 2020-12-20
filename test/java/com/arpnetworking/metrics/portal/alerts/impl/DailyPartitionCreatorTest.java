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
import akka.pattern.Patterns;
import akka.testkit.javadsl.TestKit;
import com.arpnetworking.commons.java.time.ManualClock;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.google.common.base.MoreObjects;
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
import java.util.concurrent.TimeUnit;
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
    private static final String TEST_SCHEMA = "TEST_SCHEMA";
    private static final String TEST_TABLE = "TEST_TABLE";
    private static final Duration MSG_TIMEOUT = Duration.ofSeconds(1);
    private static final long TEST_LOOKAHEAD = 7;

    private static final Instant CLOCK_START = Instant.parse("2020-08-13T00:00:00Z");

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
        _clock = new ManualClock(CLOCK_START, Duration.ofDays(1), ZoneOffset.UTC);

        _actorSystem = ActorSystem.create();
        _probe = new TestKit(_actorSystem);
    }

    private ActorRef createActor() {
        return createActor(() -> { });
    }

    @SuppressFBWarnings("SIC_INNER_SHOULD_BE_STATIC_ANON")
    private ActorRef createActor(final Runnable onExecute) {
        // Create an actor with the db execution behavior mocked out.
        final Props props = Props.create(
                DailyPartitionCreator.class,
                () -> new DailyPartitionCreator(
                        _server,
                        _metrics,
                        TEST_SCHEMA,
                        TEST_TABLE,
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
                        onExecute.run();
                        _probe.getRef().tell(
                                new ExecuteCall(schema, table, startDate, endDate),
                                _probe.getRef()
                        );
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

        // The actor will tick on startup.
        ExecuteCall call = _probe.expectMsgClass(ExecuteCall.class);
        long clockDifference = ChronoUnit.DAYS.between(
                call.getStart(),
                ZonedDateTime.ofInstant(_clock.instant(), _clock.getZone())
        );
        assertThat("range should start from current date", clockDifference, equalTo(0L));
        assertThat(call.getSchema(), equalTo(TEST_SCHEMA));
        assertThat(call.getTable(), equalTo(TEST_TABLE));

        for (int i = 0; i < 3; i++) {
            // Clock didn't move
            ref.tell(DailyPartitionCreator.TICK, _probe.getRef());
            _probe.expectNoMessage(MSG_TIMEOUT);

            // Clock moved 1 day
            _clock.tick();
            ref.tell(DailyPartitionCreator.TICK, _probe.getRef());
            call = _probe.expectMsgClass(ExecuteCall.class);

            assertThat(call.getSchema(), equalTo(TEST_SCHEMA));
            assertThat(call.getTable(), equalTo(TEST_TABLE));

            final long difference = ChronoUnit.DAYS.between(call.getStart(), call.getEnd());
            assertThat("range should respect lookahead", difference, equalTo(TEST_LOOKAHEAD));

            clockDifference = ChronoUnit.DAYS.between(
                    call.getStart(),
                    ZonedDateTime.ofInstant(_clock.instant(), _clock.getZone())
            );
            assertThat("range should start from current date", clockDifference, equalTo(0L));
        }
        Patterns.gracefulStop(ref, MSG_TIMEOUT).toCompletableFuture().get(1, TimeUnit.SECONDS);
        _probe.expectTerminated(ref);
    }

    @Test
    public void testCreatePartitionsOnDemand() throws Exception {
        final ZonedDateTime clockStart = ZonedDateTime.ofInstant(CLOCK_START, ZoneOffset.UTC);
        final ZonedDateTime oneWeekAgo = clockStart.minusDays(7);
        final LocalDate oneWeekAgoLocal = oneWeekAgo.toLocalDate();
        final ActorRef ref = createActor();

        // The actor will tick on startup.
        _probe.expectMsgClass(ExecuteCall.class);
;
        DailyPartitionCreator.ensurePartitionExistsForInstant(ref, oneWeekAgo.toInstant(), MSG_TIMEOUT)
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS);
        final ExecuteCall call = _probe.expectMsgClass(ExecuteCall.class);
        assertThat(call.getStart(), equalTo(oneWeekAgoLocal));
        assertThat(call.getEnd(), equalTo(oneWeekAgoLocal.plusDays(1)));
        assertThat(call.getSchema(), equalTo(TEST_SCHEMA));
        assertThat(call.getTable(), equalTo(TEST_TABLE));

        DailyPartitionCreator.ensurePartitionExistsForInstant(ref, oneWeekAgo.toInstant(), MSG_TIMEOUT)
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS);
        _probe.expectNoMessage(); // should have been cached

        Patterns.gracefulStop(ref, MSG_TIMEOUT).toCompletableFuture().get();
        _probe.expectTerminated(ref);
    }

    @Test(expected = ExecutionException.class)
    public void testExecutionError() throws Exception {
        final ActorRef ref = createActor(
                () -> {
                    throw new PersistenceException("Something went wrong");
                }
        );
        DailyPartitionCreator.ensurePartitionExistsForInstant(ref, CLOCK_START, MSG_TIMEOUT)
            .toCompletableFuture()
            .get(1, TimeUnit.SECONDS);
    }

    /**
     * Helper message class used by the test probe in this suite to inspect arguments to DailyPartionCreator#execute.
     */
    public static final class ExecuteCall {
        private final String _schema;
        private final String _table;
        private final LocalDate _start;
        private final LocalDate _end;

        public ExecuteCall(final String schema, final String table, final LocalDate start, final LocalDate end) {
            _schema = schema;
            _table = table;
            _start = start;
            _end = end;
        }

        public String getSchema() {
            return _schema;
        }

        public String getTable() {
            return _table;
        }

        public LocalDate getStart() {
            return _start;
        }

        public LocalDate getEnd() {
            return _end;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("_schema", _schema)
                    .add("_table", _table)
                    .add("_start", _start)
                    .add("_end", _end)
                    .toString();
        }
    }
}
