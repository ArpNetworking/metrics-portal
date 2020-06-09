/*
 * Copyright 2019 Dropbox Inc.
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

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public class RollupForwarder extends AbstractActor {
    private final ActorRef _rollupManager;

    /**
     * {@link RollupForwarder} actor constructor.
     *
     * @param rollupManager actor ref to RollupManager actor
     * @param metrics periodic metrics instance to log stats to
     */
    @Inject
    public RollupForwarder(
            @Named("RollupManager") final ActorRef rollupManager,
            final PeriodicMetrics metrics
    ) {
        _rollupManager = rollupManager;
        _metrics = metrics;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchAny(msg -> {
                    _rollupManager.forward(msg, context());
                    LOGGER.debug()
                            .setMessage("forwarding message")
                            .addData("msg", msg)
                            .log();
                    _metrics.recordCounter("rollup/forwarder/forwarded", 1);
                })
                .build();
    }

    private final PeriodicMetrics _metrics;
    private static final Logger LOGGER = LoggerFactory.getLogger(RollupForwarder.class);
}
