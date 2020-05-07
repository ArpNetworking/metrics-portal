/*
 * Copyright 2020 Dropbox, Inc.
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

package models.internal.alerts;

import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.google.common.collect.ImmutableMap;

import java.time.Duration;
import java.util.UUID;

/**
 * Internal model for an alert.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public interface Alert {
    /**
     * The unique identifier for this alert.
     *
     * @return The UUID
     */
    UUID getId();

    /**
     * A human-readable name for this alert.
     *
     * @return The alert name.
     */
    String getName();

    /**
     * The description of this alert.
     * <p>
     * This generally includes an explanation of the behavior that the alert query attempts to capture, as well
     * as possible remediation steps.
     *
     * @return The alert description.
     */
    String getDescription();

    /**
     * The query to evaluate.
     * <p>
     * If an alert query evaluates to a non-zero series, then it is considered firing.
     *
     * @return the alert query.
     */
    MetricsQuery getQuery();

    /**
     * The period used to evaluate this alert.
     *
     * @return the alert period.
     */
    Duration getPeriod();

    /**
     * Returns {@code true} iff this alert is enabled.
     *
     * @return true if this alert is enabled, otherwise false.
     */
    boolean isEnabled();

    /**
     * Implementation-defined metadata for this alert.
     * <p>
     * The contents of this field are intentionally left unspecified and are up to the particular alert implementation.
     * <p>
     * This is intended to act as a mechanism for implementations to pass around additional context outside of this interface
     * (e.g. fields configured elsewhere that may be used outside of metrics portal).
     *
     * Particular implementations of {@link AlertRepository} may make use of this field, but that is not required.
     *
     * @return the metadata.
     */
    ImmutableMap<String, Object> getAdditionalMetadata();
}
