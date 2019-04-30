/*
 * Copyright 2014 Groupon.com
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
package com.arpnetworking.metrics.portal.hosts.impl;

import akka.actor.AbstractActor;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.typesafe.config.Config;

/**
 * This is a placeholder actor that does not actually find any hosts.
 * The primary purpose of this class is to demonstrate how to implement a
 * host provider.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public class NoHostProvider extends AbstractActor {

    /**
     * Public constructor.
     *
     * @param configuration Play configuration.
     */
    @Inject
    public NoHostProvider(@Assisted final Config configuration) {
        getContext().system().scheduler().schedule(
                ConfigurationHelper.getFiniteDuration(configuration, "initialDelay"),
                ConfigurationHelper.getFiniteDuration(configuration, "interval"),
                getSelf(),
                TICK,
                getContext().dispatcher(),
                getSelf());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals(TICK, tick -> {
                    LOGGER.trace()
                            .setMessage("Searching for added/updated/deleted hosts")
                            .addData("actor", self())
                            .log();
                    LOGGER.debug().setMessage("No hosts found!").log();
                })
                .build();
    }

    private static final String TICK = "tick";
    private static final Logger LOGGER = LoggerFactory.getLogger(NoHostProvider.class);
}
