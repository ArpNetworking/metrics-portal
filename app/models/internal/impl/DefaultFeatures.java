/*
 * Copyright 2016 Inscope Metrics, Inc.
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
package models.internal.impl;

import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;
import models.internal.Features;

/**
 * Default internal model implementation of features.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
@Loggable
public final class DefaultFeatures implements Features {

    @Override
    public boolean isTelemetryEnabled() {
        return _telemetryEnabled;
    }

    @Override
    public boolean isProxyEnabled() {
        return _proxyEnabled;
    }

    @Override
    public boolean isProxyPreferred() {
        return _proxyPreferred;
    }

    @Override
    public boolean isHostRegistryEnabled() {
        return _hostRegistryEnabled;
    }

    @Override
    public boolean isAlertsEnabled() {
        return _alertsEnabled;
    }

    @Override
    public boolean isRollupsEnabled() {
        return _rollupsEnabled;
    }

    public boolean isReportsEnabled() {
        return _reportsEnabled;
    }

    @Override
    public ImmutableList<Integer> getMetricsAggregatorDaemonPorts() {
        return _metricsAggregatorDaemonPorts;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("{telemetryEnabled=").append(_telemetryEnabled)
                .append(", proxyEnabled=").append(_proxyEnabled)
                .append(", proxyPreferred=").append(_proxyPreferred)
                .append(", hostRegistryEnabled=").append(_hostRegistryEnabled)
                .append(", alertsEnabled=").append(_alertsEnabled)
                .append(", rollupsEnabled=").append(_rollupsEnabled)
                .append(", reportsEnabled=").append(_reportsEnabled)
                .append(", metricsAggregatorDaemonPorts=").append(_metricsAggregatorDaemonPorts)
                .append("}")
                .toString();
    }

    /**
     * Public constructor.
     *
     * @param configuration the {@code Config} instance.
     */
    public DefaultFeatures(final Config configuration) {
        _telemetryEnabled = configuration.getBoolean("portal.features.telemetry.enabled");
        _proxyEnabled = configuration.getBoolean("portal.features.proxy.enabled");
        _proxyPreferred = configuration.getBoolean("portal.features.proxy.preferred");
        _hostRegistryEnabled = configuration.getBoolean("portal.features.hostRegistry.enabled");
        _alertsEnabled = configuration.getBoolean("portal.features.alerts.enabled");
        _rollupsEnabled = configuration.getBoolean("portal.features.rollups.enabled");
        _reportsEnabled = configuration.getBoolean("portal.features.reports.enabled");
        _metricsAggregatorDaemonPorts = ImmutableList.copyOf(
                configuration.getIntList("portal.features.metricsAggregatorDaemonPorts"));
    }

    private final boolean _telemetryEnabled;
    private final boolean _proxyEnabled;
    private final boolean _proxyPreferred;
    private final boolean _hostRegistryEnabled;
    private final boolean _alertsEnabled;
    private final boolean _rollupsEnabled;
    private final boolean _reportsEnabled;
    private final ImmutableList<Integer> _metricsAggregatorDaemonPorts;
}
