/*
 * Copyright 2016 Inscope Metrics Inc.
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

import com.arpnetworking.metrics.portal.hosts.HostRepository;
import com.arpnetworking.metrics.portal.organizations.OrganizationRepository;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.inject.assistedinject.Assisted;
import com.typesafe.config.Config;
import jakarta.inject.Inject;
import models.internal.MetricsSoftwareState;
import models.internal.impl.DefaultHost;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.Status;
import org.apache.pekko.pattern.Patterns;
import play.libs.ws.WSClient;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Host provider that uses the Consul API to get host data.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class ConsulHostProvider extends AbstractActor {

    /**
     * Public constructor.
     *
     * @param hostRepository Repository to store hosts.
     * @param organizationRepository Repository to store organizations.
     * @param wsClient Webservice client used to make HTTP service calls.
     * @param configuration Play configuration.
     */
    @Inject
    public ConsulHostProvider(
            final HostRepository hostRepository,
            final OrganizationRepository organizationRepository,
            final WSClient wsClient,
            @Assisted final Config configuration) {
        _hostRepository = hostRepository;
        _organizationRepository = organizationRepository;
        _targetOrganizationId = UUID.fromString(configuration.getString("targetOrganizationId"));
        getContext().system().scheduler().scheduleAtFixedRate(
                ConfigurationHelper.getFiniteDuration(configuration, "initialDelay"),
                ConfigurationHelper.getFiniteDuration(configuration, "interval"),
                getSelf(),
                TICK,
                getContext().dispatcher(),
                getSelf());
        _client = new ConsulClient.Builder()
                .setBaseUrl(URI.create(configuration.getString("baseUrl")))
                .setQuery(configuration.getString("query"))
                .setClient(wsClient)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals(TICK, tick -> {
                    LOGGER.info()
                            .setMessage("Searching for added/updated hosts")
                            .addData("actor", self())
                            .log();
                    Patterns.pipe(_client.getHostList(), context().dispatcher()).to(self(), self());
                })
                .matchUnchecked(List.class, (List<ConsulClient.Host> hostList) -> {
                    for (final ConsulClient.Host host : hostList) {
                        final models.internal.Host dh = new DefaultHost.Builder()
                                .setHostname(host.getNode())
                                .setMetricsSoftwareState(MetricsSoftwareState.UNKNOWN)
                                .build();
                        _hostRepository.addOrUpdateHost(dh, _organizationRepository.get(_targetOrganizationId));
                    }
                })
                .match(Status.Failure.class, failure -> {
                    LOGGER.warn()
                            .setMessage("Failure processing Consul response")
                            .addData("actor", self())
                            .setThrowable(failure.cause())
                            .log();
                })
                .build();
    }

    private final HostRepository _hostRepository;
    private final OrganizationRepository _organizationRepository;
    private final UUID _targetOrganizationId;
    private final ConsulClient _client;

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsulHostProvider.class);
    private static final String TICK = "tick";
}
