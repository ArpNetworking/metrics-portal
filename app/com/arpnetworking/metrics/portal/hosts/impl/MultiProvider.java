/*
 * Copyright 2016 Smartsheet.com
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

import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueType;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.Props;
import play.Environment;

import java.util.Set;

/**
 * Provider that starts multiple sub providers.
 *
 * The MultiProvider operates by looking at the hostProvider config block and iterating over each key.  If the
 * key is an object with a type subkey, we will attempt to instantiate it. We leverage the {@link HostProviderFactory}
 * class to create a {@link Props} with the {@link Config} built from the subkey.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class MultiProvider extends AbstractActor {
    /**
     * Public constructor.
     *
     * @param factory a {@link HostProviderFactory}.
     * @param environment Play environment.
     * @param configuration Play configuration.
     */
    @Inject
    @SuppressFBWarnings(value = "MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR", justification = "getContext() and getSelf() are safe to call")
    public MultiProvider(final HostProviderFactory factory, final Environment environment, @Assisted final Config configuration) {
        final Set<String> entries = configuration.root().keySet();
        for (String key : entries) {
            if (configuration.getValue(key).valueType() == ConfigValueType.OBJECT) {
                // Make sure that we're looking at an Object with a .type subkey
                if (!configuration.hasPath(key + ".type")) {
                    LOGGER.warn()
                            .setMessage("Expected type for host provider")
                            .addData("key", key)
                            .log();
                    continue;
                }

                // Create the child configuration, with a fallback to the current config for things like "interval"
                final Config subConfig = configuration.getConfig(key).withFallback(configuration);

                // Create the props and launch
                final Props subProps = factory.create(subConfig, ConfigurationHelper.getType(environment, subConfig, "type"));
                getContext().actorOf(subProps);
            }
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .build();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiProvider.class);
}
