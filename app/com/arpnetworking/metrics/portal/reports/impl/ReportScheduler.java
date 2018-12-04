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

import akka.actor.Props;
import akka.persistence.AbstractPersistentActorWithTimers;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;

import com.arpnetworking.metrics.portal.reports.ReportRepository;
import scala.concurrent.duration.Duration;

public class ReportScheduler extends AbstractPersistentActorWithTimers {

    /**
     * Props factory.
     *
     * @param repository a
     * @return a new props to create this actor
     */
    public static Props props(final ReportRepository repository) {
        return Props.create(ReportScheduler.class, () -> new ReportScheduler(repository));
    }

    public static class Job implements Serializable {
        public String id;
        public Instant nextRun;
        public TemporalAmount period;
        public Job(String id, Instant nextRun, TemporalAmount period) {
            this.id = id;
            this.nextRun = nextRun;
            this.period = period;
        }
        private static final long serialVersionUID = 1L;
    }

    private static class Tick implements Serializable {
        private static final long serialVersionUID = 1L;
    }
    public static class Schedule implements Serializable {
        public Job job;
        public Schedule(Job job) {
            this.job = job;
        }
        private static final long serialVersionUID = 1L;
    }

    private PriorityQueue<Job> plan = new PriorityQueue<>();

    private ReportRepository repository;
    public ReportScheduler(final ReportRepository repository) {
        this.repository = repository;
        timers().startPeriodicTimer("TICK", new Tick(), Duration.apply(1, TimeUnit.SECONDS));
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
                .match(Tick.class, e -> {
                    System.out.println("(recovering) tick; "+plan.size()+" elements in plan; first is for "+(plan.peek()==null ? "<never>" : plan.peek().nextRun.toString()));
                    while (plan.peek().nextRun.isBefore(Instant.now())) {
                        plan.remove();
                    }
                })
                .match(Schedule.class, e -> plan.add(e.job))
                .build();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Tick.class, e -> {
                    System.out.println("tick; "+plan.size()+" elements in plan; first is for "+(plan.peek()==null ? "<never>" : plan.peek().nextRun.toString()));
                    while (plan.peek().nextRun.isBefore(Instant.now())) {
                        Job j = plan.peek();
                        self().tell(new Schedule(new Job(j.id, j.nextRun.plus(j.period), j.period)), self());
                        plan.remove();
                        System.out.println("Running job: "+j.id);
                        repository.getSpec(j.id);
                    }
                })
                .match(Schedule.class, e -> plan.add(e.job))
                .build();
    }

    @Override
    public String persistenceId() {
        return "com.arpnetworking.metrics.portal.reports.impl.ReportScheduler";
    }
}
