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
package com.arpnetworking.metrics.portal.hosts;

import models.internal.Host;
import models.internal.HostQuery;
import models.internal.MetricsSoftwareState;
import models.internal.Organization;
import models.internal.QueryResult;

import java.util.Optional;

/**
 * Interface for repository of hosts available for metrics. The repository is
 * designed around the host name as the primary key.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 * @author Ting Tu (tingtu at groupon dot com)
 */
public interface HostRepository extends AutoCloseable {

    /**
     * Open the <code>HostRepository</code>.
     */
    void open();

    /**
     * Close the <code>HostRepository</code>.
     */
    void close();

    /**
     * Retrieve from the repository.
     *
     * @param hostname The hostname of the host to remove.
     * @param organization The organization owning the host.
     * @return The matching <code>Host</code> if found or <code>Optional.empty()</code>.
     */
    Optional<Host> getHost(String hostname, Organization organization);

    /**
     * Add a new host or update an existing host in the repository.
     *
     * @param host The host to add to the repository.
     * @param organization The organization owning the host.
     */
    void addOrUpdateHost(Host host, Organization organization);

    /**
     * Remove the host by hostname from the repository.
     *
     * @param hostname The hostname of the host to remove.
     * @param organization The organization owning the host.
     */
    void deleteHost(String hostname, Organization organization);

    /**
     * Create a query against the hosts repository.
     *
     * @param organization Organization to search in.
     * @return Instance of <code>HostQuery</code>.
     */
    HostQuery createHostQuery(Organization organization);

    /**
     * Query the hosts repository.
     *
     * @param query Instance of <code>HostQuery</code>.
     * @return Instance of <code>HostQueryResult</code>.
     */
    QueryResult<Host> query(HostQuery query);

    /**
     * Retrieve the total number of hosts in the repository.
     *
     * @param organization The organization owning the hosts.
     * @return The total number of hosts.
     */
    long getHostCount(Organization organization);

    /**
     * Retrieve the number of hosts with metrics software in the specified
     * state.
     *
     * @param metricsSoftwareState The state to filter on.
     * @param organization The organization owning the host.
     * @return The number of hosts in the specified state.
     */
    long getHostCount(MetricsSoftwareState metricsSoftwareState, Organization organization);
}
