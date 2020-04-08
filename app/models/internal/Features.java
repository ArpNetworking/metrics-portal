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
package models.internal;

import com.arpnetworking.metrics.portal.reports.RecipientType;
import com.arpnetworking.metrics.portal.reports.ReportFormat;
import com.arpnetworking.metrics.portal.reports.ReportInterval;
import com.arpnetworking.metrics.portal.reports.SourceType;
import com.google.common.collect.ImmutableList;

/**
 * Internal model interface for metrics portal feature state.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public interface Features {

    /**
     * Telemetry feature.
     *
     * @return true if and only if telemetry is enabled.
     */
    boolean isTelemetryEnabled();

    /**
     * Proxy feature.
     *
     * @return true if and only if proxy is enabled.
     */
    boolean isProxyEnabled();

    /**
     * Proxy feature. Sets proxy before direct connect.
     *
     * @return true if and only if proxy is tried first.
     */
    boolean isProxyPreferred();

    /**
     * Host registry feature.
     *
     * @return true if and only if host registry is enabled.
     */
    boolean isHostRegistryEnabled();

    /**
     * Alerts feature.
     *
     * @return true if and only if alerts is enabled.
     */
    boolean isAlertsEnabled();

    /**
     * Rollups feature.
     *
     * @return true if and only if rollups is enabled.
     */
    boolean isRollupsEnabled();

    /**
     * Reports feature.
     *
     * @return true if and only if reports is enabled.
     */
    boolean isReportsEnabled();

    /**
     * Metrics aggregator daemon ports.
     *
     * @return list of ports for metrics aggregator daemon (or its proxies).
     */
    ImmutableList<Integer> getMetricsAggregatorDaemonPorts();

    /**
     * Reporting {@link SourceType}s that are enabled.
     *
     * @return list of enabled {@link SourceType} values.
     */
    ImmutableList<SourceType> getReportingSourceTypes();

    /**
     * Reporting {@link ReportFormat}s that are enabled.
     *
     * @return list of {@link ReportFormat} values.
     */
    ImmutableList<ReportFormat> getReportingReportFormats();

    /**
     * Reporting {@link RecipientType}s that are enabled.
     *
     * @return list of {@link RecipientType} values.
     */
    ImmutableList<RecipientType> getReportingRecipientTypes();

    /**
     * Names of reporting {@link RecipientType}s that are enabled.
     *
     * @return list of {@link RecipientType} values.
     */
    ImmutableList<ReportInterval> getReportingIntervals();
}
