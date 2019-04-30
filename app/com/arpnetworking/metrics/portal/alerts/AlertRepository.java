/*
 * Copyright 2015 Groupon.com
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
package com.arpnetworking.metrics.portal.alerts;

import models.internal.Alert;
import models.internal.AlertQuery;
import models.internal.Organization;
import models.internal.QueryResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Interface for repository of alerts.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public interface AlertRepository {

    /**
     * Open this {@link AlertRepository}.
     */
    void open();

    /**
     * Close this {@link AlertRepository}.
     */
    void close();

    /**
     * Get the {@link Alert} by identifier and {@link Organization}.
     *
     * @param identifier The {@link Alert} identifier.
     * @param organization The {@link Organization} owning the alert.
     * @return The matching {@link Alert} if found or else {@code Optional.empty()}.
     */
    Optional<Alert> getAlert(UUID identifier, Organization organization);

    /**
     * Delete an {@link Alert} by identifier.
     *
     * @param identifier The {@link Alert} identifier.
     * @param organization The {@link Organization} owning the alert.
     * @return The number of alerts deleted; should be 1 or 0.
     */
    int deleteAlert(UUID identifier, Organization organization);

    /**
     * Create a query against the alerts repository.
     *
     * @param organization {@link Organization} to search in.
     * @return Instance of {@link AlertQuery}.
     */
    AlertQuery createAlertQuery(Organization organization);

    /**
     * Query alerts.
     *
     * @param query Instance of {@link AlertQuery}.
     * @return The {@code Collection} of all alerts.
     */
    QueryResult<Alert> queryAlerts(AlertQuery query);

    /**
     * Retrieve the total number of alerts in the repository for an {@link Organization}.
     *
     * @param organization The {@link Organization} owning the alerts.
     * @return The total number of alerts for the {@link Organization}.
     */
    long getAlertCount(Organization organization);

    /**
     * Add a new {@link Alert} or update the existing one in the repository.
     *
     * @param alert The {@link Alert} to add to or update in the repository.
     * @param organization The {@link Organization} owning the alert.
     */
    void addOrUpdateAlert(Alert alert, Organization organization);
}
