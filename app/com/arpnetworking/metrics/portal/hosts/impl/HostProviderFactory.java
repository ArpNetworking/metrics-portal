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

import com.arpnetworking.commons.pekko.GuiceActorCreator;
import com.arpnetworking.utility.ConfigurationOverrideModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.typesafe.config.Config;
import org.apache.pekko.actor.Actor;
import org.apache.pekko.actor.Props;

/**
 * Factory for creating HostProviders with nested/changed Configurations.
 *
 * The goal of this class is to create a host provider with a configuration that is not the root config. This allows
 * host providers to only care about their immediate config values and still use Guice's dependency injection.
 * Since Guice does not allow overriding of any bindings, we leverage the @Assisted scope. Given a configuration to use,
 * we create a child injector with a module {@link ConfigurationOverrideModule} that only binds the
 * {@link Config} in the @Assisted scope.  We then use the GuiceActorCreator to create the {@link Props} to
 * create the actor.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class HostProviderFactory {
    /**
     * Public constructor.
     *
     * @param injector Injector to create objects with
     */
    @Inject
    public HostProviderFactory(final Injector injector) {
        _injector = injector;
    }

    /**
     * Builds a host provider actor {@link Props}.
     *
     * @param config The config to give to the host provider
     * @param clazz the class of the host provider
     * @return A new {@link Props} for constructing the actor
     */
    public Props create(final Config config, final Class<? extends Actor> clazz) {
        // Create new injector with bound config
        final Injector childInjector = _injector.createChildInjector(new ConfigurationOverrideModule(config));
        // Create the Props
        return GuiceActorCreator.props(childInjector, clazz);
    }

    private final Injector _injector;
}
