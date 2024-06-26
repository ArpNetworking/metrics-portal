/*
 * Copyright 2014 Groupon.com
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

import java.io.Serializable;
import java.util.Optional;

/**
 * Internal model interface for a host query.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public interface HostQuery extends Serializable {

    /**
     * Set the hostname to query for. Optional. Defaults to all hosts. If this field is not set it is strongly
     * encouraged that the {@code limit} field is set.
     *
     * @param partialHostname The partial or complete hostname to match.
     * @return This instance of {@link HostQuery}.
     */
    HostQuery partialHostname(Optional<String> partialHostname);

    /**
     * Set the metrics software state to query for. Optional. Defaults to any state.
     *
     * @param metricsSoftwareState The metrics software state to match.
     * @return This instance of {@link HostQuery}.
     */
    HostQuery metricsSoftwareState(Optional<MetricsSoftwareState> metricsSoftwareState);

    /**
     * Set the cluster to query for. Optional. Defaults to all clusters.
     *
     * @param cluster The complete cluster to match.
     * @return This instance of {@link HostQuery}.
     */
    HostQuery cluster(Optional<String> cluster);

    /**
     * The maximum number of hosts to return. Optional. Default is 1000.
     *
     * @param limit The maximum number of hosts to return.
     * @return This instance of {@link HostQuery}.
     */
    HostQuery limit(int limit);

    /**
     * The offset into the result set. Optional. Default is not set.
     *
     * @param offset The offset into the result set.
     * @return This instance of {@link HostQuery}.
     */
    HostQuery offset(Optional<Integer> offset);

    /**
     * Sort the results by the specified field. Optional. Default sorting is defined by the underlying repository
     * implementation but it is strongly recommended that the repository make some attempt to sort by score or relevance
     * given the inputs.
     *
     * @param field The {@link Field} to sort on.
     * @return This instance of {@link HostQuery}.
     */
    HostQuery sortBy(Optional<Field> field);

    /**
     * Accessor for the hostname.
     *
     * @return The hostname.
     */
    Optional<String> getPartialHostname();

    /**
     * Accessor for the cluster.
     *
     * @return The cluster.
     */
    Optional<String> getCluster();

    /**
     * Accessor for the organization.
     *
     * @return The organization.
     */
    Organization getOrganization();

    /**
     * Accessor for the metrics software state.
     *
     * @return The metrics software state.
     */
    Optional<MetricsSoftwareState> getMetricsSoftwareState();

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

    /**
     * Accessor for the field to sort by.
     * @return The field to sort by.
     */
    Optional<Field> getSortBy();

    /**
     * The fields defined for a host.
     */
    enum Field {
        /**
         * The hostname.
         */
        HOSTNAME,
        /**
         * The state of the metrics software.
         */
        METRICS_SOFTWARE_STATE;
    }
}
