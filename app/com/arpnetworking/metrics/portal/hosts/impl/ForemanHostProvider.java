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

import akka.actor.AbstractActor;
import akka.actor.Status;
import akka.pattern.PatternsCS;
import com.arpnetworking.metrics.portal.hosts.HostRepository;
import com.arpnetworking.metrics.portal.organizations.OrganizationRepository;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.typesafe.config.Config;
import models.internal.Host;
import models.internal.MetricsSoftwareState;
import models.internal.impl.DefaultHost;
import play.libs.ws.WSClient;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Host provider that uses the Foreman API to get host data.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class ForemanHostProvider extends AbstractActor {

    /**
     * Public constructor.
     *
     * @param hostRepository Repository to store hosts.
     * @param organizationRepository Repository to store organizations.
     * @param wsClient Webservice client used to make HTTP service calls.
     * @param configuration Play configuration.
     */
    @Inject
    public ForemanHostProvider(
            final HostRepository hostRepository,
            final OrganizationRepository organizationRepository,
            final WSClient wsClient,
            @Assisted final Config configuration) {
        _hostRepository = hostRepository;
        _organizationRepository = organizationRepository;
        _targetOrganizationId = UUID.fromString(configuration.getString("targetOrganizationId"));
        getContext().system().scheduler().schedule(
                ConfigurationHelper.getFiniteDuration(configuration, "initialDelay"),
                ConfigurationHelper.getFiniteDuration(configuration, "interval"),
                getSelf(),
                TICK,
                getContext().dispatcher(),
                getSelf());
        _client = new ForemanClient.Builder()
                .setBaseUrl(URI.create(configuration.getString("baseUrl")))
                .setClient(wsClient)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals(TICK, m -> {
                    LOGGER.info()
                            .setMessage("Searching for added/updated hosts")
                            .addData("actor", self())
                            .log();
                    PatternsCS.pipe(_client.getHostPage(1), context().dispatcher()).to(self(), self());
                })
                .match(ForemanClient.HostPageResponse.class, response -> {
                    final List<ForemanClient.ForemanHost> results = response.getResults();
                    for (final ForemanClient.ForemanHost host : results) {
                        final Host dh = new DefaultHost.Builder()
                                .setHostname(host.getName())
                                .setMetricsSoftwareState(MetricsSoftwareState.UNKNOWN)
                                .build();
                        _hostRepository.addOrUpdateHost(dh, _organizationRepository.get(_targetOrganizationId));
                    }

                    if (response.getTotal() > response.getPage() * response.getPerPage()) {
                        PatternsCS
                                .pipe(
                                        _client.getHostPage(
                                                response.getPage() + 1,
                                                response.getPerPage()),
                                        context().dispatcher())
                                .to(self(), self());
                    }
                })
                .match(Status.Failure.class, failure -> {
                    LOGGER.warn()
                            .setMessage("Failure processing Foreman response")
                            .addData("actor", self())
                            .setThrowable(failure.cause())
                            .log();
                })
                .build();
    }

    private final HostRepository _hostRepository;
    private final OrganizationRepository _organizationRepository;
    private final UUID _targetOrganizationId;
    private final ForemanClient _client;

    private static final Logger LOGGER = LoggerFactory.getLogger(ForemanHostProvider.class);
    private static final String TICK = "tick";
}
