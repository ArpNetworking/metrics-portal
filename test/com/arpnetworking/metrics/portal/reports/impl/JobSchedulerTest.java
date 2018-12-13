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
import com.arpnetworking.metrics.portal.reports.Job;
import com.arpnetworking.metrics.portal.reports.JobRepository;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.Duration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public class JobSchedulerTest {

    private static final Instant t0 = Instant.ofEpochMilli(0);
    private static final JobRepository repo = new MapJobRepository();
    private static final List<String> jobIds = new ArrayList<>();

    @BeforeClass
    public static void setUpClass() {
        for (int i=0; i<3; i++) {
            jobIds.add(repo.add(new Job(
                    DummyReportSpec.INSTANCE,
                    DummyReportSink.INSTANCE,
                    OneOffSchedule.INSTANCE)));
        }
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
        return castPlan(tk.receiveOne(Duration.fromNanos(1e9)));
    }

    @Test
    public void testBasics() {
        ActorSystem system = ActorSystem.create();
        TestKit tk = new TestKit(system);
        ActorRef scheduler = system.actorOf(JobScheduler.props(repo));

        Assert.assertEquals(0, getPlan(tk, scheduler).size());

        scheduler.tell(new JobScheduler.ScheduleCmd(new JobScheduler.ScheduledJob(t0, jobIds.get(0))), tk.getRef());
        tk.receiveOne(Duration.fromNanos(1e9));

        Assert.assertEquals(1, getPlan(tk, scheduler).size());
    }

}
