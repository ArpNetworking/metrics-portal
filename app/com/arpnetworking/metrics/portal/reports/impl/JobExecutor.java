package com.arpnetworking.metrics.portal.reports.impl;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.arpnetworking.metrics.portal.reports.Job;
import com.arpnetworking.metrics.portal.reports.JobRepository;
import com.arpnetworking.metrics.portal.reports.ReportSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobExecutor extends AbstractActor {
    public static Props props(final Job j) {
        return Props.create(JobExecutor.class, () -> new JobExecutor(j));
    }

    public JobExecutor(Job j) {
        j.getSpec().render().thenAccept(r -> j.getSink().send(r));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().build();
    }
}
