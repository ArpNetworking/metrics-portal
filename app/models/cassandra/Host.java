/**
 * Copyright 2017 Smartsheet.com
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
package models.cassandra;

import com.datastax.oss.driver.api.mapper.annotations.ClusteringColumn;
import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Dao;
import com.datastax.oss.driver.api.mapper.annotations.DaoFactory;
import com.datastax.oss.driver.api.mapper.annotations.Delete;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.Insert;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import com.datastax.oss.driver.api.mapper.annotations.Query;
import com.datastax.oss.driver.api.mapper.annotations.Select;
import models.internal.MetricsSoftwareState;
import models.internal.impl.DefaultHost;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;
import jakarta.persistence.Version;

/**
 * Model for alerts stored in Cassandra.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity(defaultKeyspace = "portal")
@CqlName("hosts")
public class Host {
    @Version
    private Long version;

    private Instant createdAt;

    private Instant updatedAt;

    @ClusteringColumn(0)
    private String name;

    private String cluster;

    private String metricsSoftwareState;

    @PartitionKey(0)
    private UUID organization;

    public Long getVersion() {
        return version;
    }

    public void setVersion(final Long value) {
        version = value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant value) {
        createdAt = value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final Instant value) {
        updatedAt = value;
    }

    public String getName() {
        return name;
    }

    public void setName(final String value) {
        name = value;
    }

    public UUID getOrganization() {
        return organization;
    }

    public void setOrganization(final UUID value) {
        organization = value;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(final String value) {
        cluster = value;
    }

    public String getMetricsSoftwareState() {
        return metricsSoftwareState;
    }

    public void setMetricsSoftwareState(final String value) {
        metricsSoftwareState = value;
    }

    /**
     * Converts this model into an {@link models.internal.Host}.
     *
     * @return a new internal model
     */
    public models.internal.Host toInternal() {
        final DefaultHost.Builder builder = new DefaultHost.Builder()
                .setHostname(getName())
                .setCluster(getCluster())
                .setMetricsSoftwareState(MetricsSoftwareState.valueOf(getMetricsSoftwareState()));
        return builder.build();
    }

    /**
     * Queries for hosts.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    @Dao
    public interface HostQueries {
        /**
         * Queries for all hosts in an organization.
         *
         * @param org Organization uuid owning the hosts
         * @return Mapped query results
         */
        @Query("select * from ${keyspaceId}.hosts where organization = :org")
        Stream<Host> getHostsForOrganization(UUID org);

        /**
         * Gets a host by organization and name.
         *
         * @param organizationId the id of the organization
         * @param name the name of the host
         *
         * @return A future host or null if none found
         */
        @Select
        CompletionStage<Host> get(UUID organizationId, String name);

        /**
         * Saves a host record.
         *
         * @param host the host
         */
        @Insert
        void save(Host host);

        /**
         * Deletes a host record.
         *
         * @param organizationId the organization id that owns the host
         * @param name the name of the host
         */
        @Delete(entityClass = Host.class)
        void delete(UUID organizationId, String name);
    }

    /**
     * Mapper for Host queries.
     */
    @com.datastax.oss.driver.api.mapper.annotations.Mapper
    public interface Mapper {
        /**
         * Gets the DAO.
         *
         * @return the DAO
         */
        @DaoFactory
        HostQueries dao();

    }
}
// CHECKSTYLE.ON: MemberNameCheck
