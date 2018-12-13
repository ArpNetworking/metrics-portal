/*
 * Copyright 2018 Dropbox
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
package com.arpnetworking.metrics.portal.reports.impl;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import com.arpnetworking.commons.java.time.ManualClock;
import com.arpnetworking.metrics.portal.AkkaClusteringConfigFactory;
import com.arpnetworking.metrics.portal.reports.Job;
import com.arpnetworking.metrics.portal.reports.JobRepository;
import com.arpnetworking.metrics.portal.reports.Report;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import scala.concurrent.duration.Duration;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class JobSchedulerTest {

    private static final Instant t0 = Instant.ofEpochMilli(0);
    private JobRepository repo = new MapJobRepository();
    private List<String> jobIds = new ArrayList<>();

    private ManualClock clock;
    private ActorSystem system;

    private static final AtomicLong systemNameNonce = new AtomicLong(0);

    @Before
    public void setUp() {
        clock = new ManualClock(t0, java.time.Duration.ofSeconds(1), ZoneId.systemDefault());
        for (int i=0; i<3; i++) {
            jobIds.add(repo.add(new Job(
                    DummyReportSpec.INSTANCE,
                    DummyReportSink.INSTANCE,
                    OneOffSchedule.INSTANCE)));
        }

        system = ActorSystem.create(
                "test-"+systemNameNonce.getAndIncrement(),
                ConfigFactory.parseMap(AkkaClusteringConfigFactory.generateConfiguration()));
    }

    @After
    public void tearDown() {
        system.terminate();
    }

    private PriorityQueue<JobScheduler.ScheduledJob> castPlan(Object plan) {
        PriorityQueue<JobScheduler.ScheduledJob> result = new PriorityQueue<>();
        for (Object o : (PriorityQueue) plan) {
            result.add((JobScheduler.ScheduledJob) o);
        }
        return result;
    }

    private PriorityQueue<JobScheduler.ScheduledJob> getPlan(TestKit tk, ActorRef scheduler) {
        scheduler.tell(JobScheduler.GetPlanCmd.INSTANCE, tk.getRef());
        return castPlan(tk.receiveOne(Duration.fromNanos(30e9)));
    }

    @Test
    public void testBasics() {
        TestKit tk = new TestKit(system);
        ActorRef scheduler = system.actorOf(JobScheduler.props(repo, clock));

        Assert.assertEquals(0, getPlan(tk, scheduler).size());

        scheduler.tell(new JobScheduler.ScheduleCmd(new JobScheduler.ScheduledJob(t0, jobIds.get(0))), tk.getRef());
        tk.expectMsg(true);

        Assert.assertEquals(1, getPlan(tk, scheduler).size());
    }

    @Test
    public void testTick() {
        final Report report = new Report("egrraeg", "hhloio".getBytes());
        final AtomicReference<Report> sentReport = new AtomicReference<>();
        String jobId = repo.add(new Job(
                () -> CompletableFuture.completedFuture(report),
                fr -> {fr.thenAccept(sentReport::set); return CompletableFuture.completedFuture(null);},
                OneOffSchedule.INSTANCE));

        TestKit tk = new TestKit(system);
        ActorRef scheduler = system.actorOf(JobScheduler.props(repo, clock));
        scheduler.tell(new JobScheduler.ScheduleCmd(new JobScheduler.ScheduledJob(t0, jobId)), tk.getRef());
        tk.expectMsg(true);

        scheduler.tell(JobScheduler.Tick.INSTANCE, tk.getRef());
        PriorityQueue<JobScheduler.ScheduledJob> plan = getPlan(tk, scheduler);
        Assert.assertEquals(1, plan.size());
        Assert.assertEquals(jobId, plan.peek().getJobId());

        clock.tick();

        scheduler.tell(JobScheduler.Tick.INSTANCE, tk.getRef());
        tk.expectMsg(JobExecutor.Success.INSTANCE);
        Assert.assertEquals(report, sentReport.get());

        Assert.assertEquals(0, getPlan(tk, scheduler).size());
    }

}
