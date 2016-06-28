/**
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

/**
 * Internal model interface for metrics portal feature state.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
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
     * Live logging feature.
     *
     * @return true if and only if live logging is enabled.
     */
    boolean isLiveLoggingEnabled();

    /**
     * Host registry feature.
     *
     * @return true if and only if host registry is enabled.
     */
    boolean isHostRegistryEnabled();

    /**
     * Expressions feature.
     *
     * @return true if and only expressions is enabled.
     */
    boolean isExpressionsEnabled();

    /**
     * Alerts feature.
     *
     * @return true if and only if alerts is enabled.
     */
    boolean isAlertsEnabled();
}
