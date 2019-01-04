/*
 * Copyright 2018 Dropbox, Inc.
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
package com.arpnetworking.metrics.portal.scheduling;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import com.arpnetworking.commons.java.time.ManualClock;
import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.scheduling.impl.MapJobRepository;
import com.arpnetworking.metrics.portal.scheduling.impl.PeriodicSchedule;
import com.typesafe.config.ConfigFactory;
import models.internal.scheduling.Job;
import models.internal.scheduling.Schedule;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import scala.concurrent.duration.Duration;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

public class JobSchedulerTest {

    private static final Instant t0 = Instant.ofEpochMilli(0);
    private static final java.time.Duration tickSize = java.time.Duration.ofSeconds(1);

    private MapJobRepository repo = new MapJobRepository();
    private ManualClock clock;
    private ActorSystem system;

    private static final AtomicLong systemNameNonce = new AtomicLong(0);

    private static final class DummyJob implements Job {
        private final UUID _uuid;

        public DummyJob(Object uuidSeed) {
            _uuid = UUID.nameUUIDFromBytes(uuidSeed.toString().getBytes());
        }

        @Override
        public UUID getId() {
            return _uuid;
        }

        @Override
        public Schedule getSchedule() {
            return new PeriodicSchedule(tickSize);
        }

        @Override
        public CompletionStage<Void> start() {
            return CompletableFuture.completedFuture(null);
        }
    }

    @Before
    public void setUp() {
        clock = new ManualClock(t0, tickSize, ZoneId.systemDefault());
        repo = new MapJobRepository();
        repo.open();
        system = ActorSystem.create(
                "test-"+systemNameNonce.getAndIncrement(),
                ConfigFactory.parseMap(AkkaClusteringConfigFactory.generateConfiguration()));
    }

    @After
    public void tearDown() {
        system.terminate();
    }

    private ArrayList<ScheduledJob> castPlan(Object plan) {
        ArrayList<ScheduledJob> result = new ArrayList<>();
        for (Object o : (PriorityQueue) plan) {
            result.add((ScheduledJob) o);
        }
        return result;
    }

    private List<ScheduledJob> getPlan(TestKit tk, ActorRef scheduler) {
        scheduler.tell(JobScheduler.GetPlanCmd.INSTANCE, tk.getRef());
        List<ScheduledJob> result = new ArrayList<>(castPlan(tk.receiveOne(Duration.fromNanos(1e9))));
        result.sort(Comparator.comparing(ScheduledJob::getWhenRun));
        return result;
    }

    @Test
    public void testBasics() {
        Job j = new DummyJob(0);
        repo.addOrUpdateJob(j);

        TestKit tk = new TestKit(system);
        ActorRef scheduler = system.actorOf(JobScheduler.props(repo, clock));

        Assert.assertTrue(getPlan(tk, scheduler).isEmpty());

        ScheduledJob job = new ScheduledJob(t0, j.getId());
        scheduler.tell(new JobScheduler.ScheduleCmd(job), tk.getRef());
        tk.expectMsg(true);

        Assert.assertEquals(Collections.singletonList(job), getPlan(tk, scheduler));
    }

    @Test
    public void testTick() {
        /* In a situation like

                   t=0                  t=1         |        t=2
                                                   job

           the first tick should do nothing; and the second tick should run+reschedule the job.
         */

        Job j = new DummyJob(0);
        repo.addOrUpdateJob(j);

        Instant t1 = t0.plus(tickSize.multipliedBy(3).dividedBy(2));

        ScheduledJob job = new ScheduledJob(t1, j.getId());

        TestKit tk = new TestKit(system);
        ActorRef scheduler = system.actorOf(JobScheduler.props(repo, clock));
        scheduler.tell(new JobScheduler.ScheduleCmd(job), tk.getRef());
        tk.expectMsg(true);
        Assert.assertEquals(Collections.singletonList(job), getPlan(tk, scheduler));

        clock.tick();
        scheduler.tell(JobScheduler.Tick.INSTANCE, tk.getRef());
        tk.expectNoMsg();
        Assert.assertEquals(Collections.singletonList(job), getPlan(tk, scheduler));

        clock.tick();
        scheduler.tell(JobScheduler.Tick.INSTANCE, tk.getRef());
        tk.expectMsg(JobExecutor.Success.INSTANCE);
        tk.expectNoMsg();

        Assert.assertEquals(
                Collections.singletonList(new ScheduledJob(t1.plus(tickSize), j.getId())),
                getPlan(tk, scheduler));
    }

}
