/*
 * Copyright 2019 Dropbox
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

import org.apache.pekko.actor.AbstractActor;
import com.arpnetworking.logback.annotations.LogValue;
import com.arpnetworking.metrics.portal.hosts.HostRepository;
import com.arpnetworking.metrics.portal.organizations.OrganizationRepository;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.steno.LogValueMapFactory;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import models.internal.MetricsSoftwareState;
import models.internal.Organization;
import models.internal.impl.DefaultHost;

import java.util.UUID;

/**
 * This is an actor that finds "configured" hosts. The primary purpose of this
 * class is to register host names that cannot otherwise be discovered but ones
 * that do not change frequently.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class ConfiguredHostProvider extends AbstractActor {

    /**
     * Public constructor.
     *
     * @param hostRepository The {@code HostRepository} instance.
     * @param organizationRepository Repository to store organizations.
     * @param configuration Play configuration.
     */
    @Inject
    public ConfiguredHostProvider(
            final HostRepository hostRepository,
            final OrganizationRepository organizationRepository,
            @Assisted final Config configuration) {
        _hostRepository = hostRepository;
        _organizationRepository = organizationRepository;
        _targetOrganizationId = UUID.fromString(configuration.getString("targetOrganizationId"));
        _hosts = configuration.getList("hosts");

        getContext().system().scheduler().scheduleOnce(
                ConfigurationHelper.getFiniteDuration(configuration, "initialDelay"),
                getSelf(),
                TICK,
                getContext().dispatcher(),
                getSelf());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals(TICK, tick -> {
                    final Organization organization = _organizationRepository.get(_targetOrganizationId);
                    for (final ConfigValue value : _hosts) {
                        if (value instanceof ConfigObject) {
                            final Config hostConfig = ((ConfigObject) value).toConfig();
                            final DefaultHost.Builder newHostBuilder = new DefaultHost.Builder();
                            if (hostConfig.hasPath("hostname")) {
                                newHostBuilder.setHostname(hostConfig.getString("hostname"));
                            } else {
                                LOGGER.warn()
                                        .setMessage("Invalid host configuration; missing hostname")
                                        .addData("rendered", value.render())
                                        .log();
                                continue;
                            }
                            if (hostConfig.hasPath("metricsSoftwareState")) {
                                newHostBuilder.setMetricsSoftwareState(
                                        MetricsSoftwareState.valueOf(hostConfig.getString("metricsSoftwareState")));
                            } else {
                                newHostBuilder.setMetricsSoftwareState(MetricsSoftwareState.UNKNOWN);
                            }
                            if (hostConfig.hasPath("cluster")) {
                                newHostBuilder.setCluster(hostConfig.getString("cluster"));
                            }
                            _hostRepository.addOrUpdateHost(newHostBuilder.build(), organization);
                        } else {
                            LOGGER.warn()
                                    .setMessage("Invalid host configuration; unsupported value type")
                                    .addData("valueType", value.valueType())
                                    .addData("rendered", value.render())
                                    .log();
                        }
                    }
                })
                .build();
    }

    /**
     * Generate a Steno log compatible representation.
     *
     * @return Steno log compatible representation.
     */
    @LogValue
    public Object toLogValue() {
        return LogValueMapFactory.builder(this)
                .put("hostRepository", _hostRepository)
                .put("organizationRepository", _organizationRepository)
                .put("targetOrganizationId", _targetOrganizationId)
                .put("hosts", _hosts)
                .build();
    }

    @Override
    public String toString() {
        return toLogValue().toString();
    }

    private final HostRepository _hostRepository;
    private final OrganizationRepository _organizationRepository;
    private final UUID _targetOrganizationId;
    private final ConfigList _hosts;

    private static final String TICK = "tick";
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfiguredHostProvider.class);
}
