/**
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

import akka.actor.Status;
import akka.actor.UntypedActor;
import akka.pattern.PatternsCS;
import com.arpnetworking.metrics.portal.hosts.HostRepository;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import models.internal.Host;
import models.internal.MetricsSoftwareState;
import models.internal.Organization;
import models.internal.impl.DefaultHost;
import play.Configuration;
import play.libs.ws.WSClient;

import java.net.URI;
import java.util.List;

/**
 * Host provider that uses the Foreman API to get host data.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class ForemanHostProvider extends UntypedActor {

    /**
     * Public constructor.
     *
     * @param hostRepository Repository to store hosts.
     * @param wsClient Webservice client used to make HTTP service calls.
     * @param configuration Play configuration.
     */
    @Inject
    public ForemanHostProvider(
            final HostRepository hostRepository,
            final WSClient wsClient,
            @Assisted final Configuration configuration) {
        _hostRepository = hostRepository;
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
    public void onReceive(final Object message) throws Exception {
        if (TICK.equals(message)) {
            LOGGER.info()
                    .setMessage("Searching for added/updated hosts")
                    .addData("actor", self())
                    .log();
            PatternsCS.pipe(_client.getHostPage(1), context().dispatcher()).to(self(), self());
        } else if (message instanceof ForemanClient.HostPageResponse) {
            final ForemanClient.HostPageResponse response = (ForemanClient.HostPageResponse) message;
            final List<ForemanClient.ForemanHost> results = response.getResults();
            for (final ForemanClient.ForemanHost host : results) {
                final Host dh = new DefaultHost.Builder()
                        .setHostname(host.getName())
                        .setMetricsSoftwareState(MetricsSoftwareState.UNKNOWN)
                        .build();
                _hostRepository.addOrUpdateHost(dh, Organization.DEFAULT);
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
        } else if (message instanceof Status.Failure) {
            final Status.Failure failure = (Status.Failure) message;
            LOGGER.warn()
                    .setMessage("Failure processing Foreman response")
                    .addData("actor", self())
                    .setThrowable(failure.cause())
                    .log();
        } else {
            LOGGER.warn()
                    .setMessage("Unhandled message")
                    .addData("actor", self())
                    .addData("akkaMessage", message)
                    .log();
            unhandled(message);
        }
    }

    private final HostRepository _hostRepository;
    private final ForemanClient _client;

    private static final Logger LOGGER = LoggerFactory.getLogger(ForemanHostProvider.class);
    private static final String TICK = "tick";
}
