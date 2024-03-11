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
package models.internal;

import models.internal.alerts.Alert;

import java.util.Optional;

/**
 * Internal model interface for an alert query.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public interface AlertQuery {

    /**
     * Set the text to query for. Optional. Defaults to no text.
     *
     * @param contains The text to match.
     * @return This instance of {@link AlertQuery}.
     */
    AlertQuery contains(String contains);

    /**
     * Filter to only alerts that are enabled. Optional. Default is not set.
     *
     * @param enabled The enabled flag.
     * @return This instance of {@link AlertQuery}.
     */
    AlertQuery enabled(boolean enabled);


    /**
     * The maximum number of alerts to return.  Optional. Default is 1000.
     *
     * @param limit The maximum number of alerts to return.
     * @return This instance of {@link AlertQuery}.
     */
    AlertQuery limit(int limit);

    /**
     * The offset into the result set. Optional. Default is not set.
     *
     * @param offset The offset into the result set.
     * @return This instance of {@link AlertQuery}.
     */
    AlertQuery offset(int offset);

    /**
     * Execute the query and return the results.
     *
     * @return The results of the query as an {@code QueryResult<Alert>} instance.
     */
    QueryResult<Alert> execute();

    /**
     * Accessor for the organization.
     *
     * @return The organization.
     */
    Organization getOrganization();

    /**
     * Accessor for the contains.
     *
     * @return The contains.
     */
    Optional<String> getContains();

    /**
     * Accessor for the enabled flag.
     *
     * @return The enabled flag.
     */
    Optional<Boolean> getEnabled();

    /**
     * Accessor for the limit.
     *
     * @return The limit.
     */
    int getLimit();

    /**
     * Accessor for the offset.
     *
     * @return The offset.
     */
    Optional<Integer> getOffset();
}
