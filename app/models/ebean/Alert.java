/**
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
package models.ebean;

import com.avaje.ebean.Model;
import com.avaje.ebean.annotation.CreatedTimestamp;
import com.avaje.ebean.annotation.UpdatedTimestamp;
import models.internal.Context;
import models.internal.Operator;

import java.sql.Timestamp;
import java.util.UUID;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * Data model for alerts.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@Table(name = "alerts", schema = "portal")
public class Alert extends Model {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @CreatedTimestamp
    @Column(name = "created_at")
    private Timestamp createdAt;

    @UpdatedTimestamp
    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @Column(name = "uuid")
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

    @OneToOne(mappedBy = "alert", cascade = CascadeType.ALL)
    private NagiosExtension nagiosExtension;

    @ManyToOne(optional = false)
    @JoinColumn(name = "organization")
    private Organization organization;

    public Long getId() {
        return id;
    }

    public void setId(final Long value) {
        id = value;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(final Long value) {
        version = value;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Timestamp value) {
        createdAt = value;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final Timestamp value) {
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

    public int getPeriod() {
        return periodInSeconds;
    }

    public void setPeriod(final int value) {
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

    public NagiosExtension getNagiosExtension() {
        return nagiosExtension;
    }

    public void setNagiosExtension(final NagiosExtension value) {
        nagiosExtension = value;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(final Organization organizationValue) {
        this.organization = organizationValue;
    }
}
// CHECKSTYLE.ON: MemberNameCheck
