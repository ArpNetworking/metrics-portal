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
import models.internal.Organization;
import models.internal.QueryResult;

import java.util.Optional;

/**
 * Interface for repository of hosts available for metrics. The repository is
 * designed around the host name as the primary key.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 * @author Ting Tu (tingtu at groupon dot com)
 */
public interface HostRepository extends AutoCloseable {

    /**
     * Open this {@link HostRepository}.
     */
    void open();

    /**
     * Close the {@link HostRepository}.
     */
    void close();

    /**
     * Retrieve a {@link Host} from the repository for an {@link Organization}.
     *
     * @param hostname The name of the host to retrieve.
     * @param organization The {@link Organization} owning the host.
     * @return The matching {@link Host} if found or else {@code Optional.empty()}.
     */
    Optional<Host> getHost(String hostname, Organization organization);

    /**
     * Add a new {@link Host} or update the existing one in the repository.
     *
     * @param host The {@link Host} to add to the repository.
     * @param organization The {@link Organization} owning the host.
     */
    void addOrUpdateHost(Host host, Organization organization);

    /**
     * Remove the {@link Host} by hostname from the repository.
     *
     * @param hostname The name of the {@link Host} to remove.
     * @param organization The {@link Organization} owning the host.
     */
    void deleteHost(String hostname, Organization organization);

    /**
     * Create a query against the hosts repository.
     *
     * @param organization {@link Organization} to search in.
     * @return Instance of {@link HostQuery}.
     */
    HostQuery createHostQuery(Organization organization);

    /**
     * Query hosts.
     *
     * @param query Instance of {@link HostQuery}.
     * @return The {@code Collection} of all hosts.
     */
    QueryResult<Host> queryHosts(HostQuery query);

    /**
     * Retrieve the total number of hosts in the repository for an {@link Organization}.
     *
     * @param organization The organization owning the hosts.
     * @return The total number of hosts.
     */
    long getHostCount(Organization organization);
}
