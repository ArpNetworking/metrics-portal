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

import akka.actor.AbstractActor;
import akka.actor.Props;
import com.arpnetworking.metrics.portal.reports.Job;

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
