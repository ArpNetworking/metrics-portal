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

import com.datastax.driver.mapping.Result;
import com.datastax.driver.mapping.annotations.Accessor;
import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.Frozen;
import com.datastax.driver.mapping.annotations.Param;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Query;
import com.datastax.driver.mapping.annotations.Table;
import models.internal.Context;
import models.internal.Operator;
import org.joda.time.Instant;

import java.util.Map;
import java.util.UUID;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Version;


/**
 * Model for alerts stored in Cassandra.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Table(name = "alerts", keyspace = "portal")
public class Alert {
    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "uuid")
    @PartitionKey
    private UUID uuid;

    @Column(name = "name")
    private String name;

    @Column(name = "cluster")
    private String cluster;

    @Column(name = "service")
    private String service;

    @Enumerated(EnumType.STRING)
    @Column(name = "context")
    private Context context;

    @Column(name = "metric")
    private String metric;

    @Column(name = "statistic")
    private String statistic;

    @Column(name = "period_in_seconds")
    private int periodInSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator")
    private Operator operator;

    @Column(name = "quantity_value")
    private double quantityValue;

    @Column(name = "quantity_unit")
    private String quantityUnit;

    @Column(name = "organization")
    private UUID organization;

    @Frozen
    @Column(name = "nagios_extensions")
    private Map<String, String> nagiosExtensions;

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

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(final UUID value) {
        uuid = value;
    }

    public String getName() {
        return name;
    }

    public void setName(final String value) {
        name = value;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(final String value) {
        cluster = value;
    }

    public String getService() {
        return service;
    }

    public void setService(final String value) {
        service = value;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(final Context value) {
        context = value;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(final String value) {
        metric = value;
    }

    public String getStatistic() {
        return statistic;
    }

    public void setStatistic(final String value) {
        statistic = value;
    }

    public int getPeriodInSeconds() {
        return periodInSeconds;
    }

    public void setPeriodInSeconds(final int value) {
        periodInSeconds = value;
    }

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(final Operator value) {
        operator = value;
    }

    public double getQuantityValue() {
        return quantityValue;
    }

    public void setQuantityValue(final double value) {
        quantityValue = value;
    }

    public String getQuantityUnit() {
        return quantityUnit;
    }

    public void setQuantityUnit(final String value) {
        quantityUnit = value;
    }

    public UUID getOrganization() {
        return organization;
    }

    public void setOrganization(final UUID value) {
        organization = value;
    }

    public Map<String, String> getNagiosExtensions() {
        return nagiosExtensions;
    }

    public void setNagiosExtensions(final Map<String, String> value) {
        nagiosExtensions = value;
    }


    /**
     * Queries for alerts.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    @Accessor
    public interface AlertQueries {
        /**
         * Queries for all alerts in an organization.
         *
         * @param organization Organization owning the alerts
         * @return Mapped query results
         */
        @Query("select * from portal.alerts_by_organization where organization = :org")
        Result<Alert> getAlertsForOrganization(@Param("org") final UUID organization);
    }
}
// CHECKSTYLE.ON: MemberNameCheck
