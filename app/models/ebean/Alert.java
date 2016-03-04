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
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * Data model for alerts.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
@Entity
@Table(name = "alerts", schema = "portal")
public class Alert extends Model {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long _id;

    @Version
    @Column(name = "version")
    private Long _version;

    @CreatedTimestamp
    @Column(name = "created_at")
    private Timestamp _createdAt;

    @UpdatedTimestamp
    @Column(name = "updated_at")
    private Timestamp _updatedAt;

    @Column(name = "uuid")
    private UUID _uuid;

    @Column(name = "name")
    private String _name;

    @Column(name = "cluster")
    private String _cluster;

    @Column(name = "service")
    private String _service;

    @Enumerated(EnumType.STRING)
    @Column(name = "context")
    private Context _context;

    @Column(name = "metric")
    private String _metric;

    @Column(name = "statistic")
    private String _statistic;

    @Column(name = "period_in_seconds")
    private int _periodInSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator")
    private Operator _operator;

    @Column(name = "quantity_value")
    private double _quantityValue;

    @Column(name = "quantity_unit")
    private String _quantityUnit;

    @OneToOne(mappedBy = "_alert", cascade = CascadeType.ALL)
    private NagiosExtension _nagiosExtension;

    public Long getId() {
        return _id;
    }

    public void setId(final Long value) {
        _id = value;
    }

    public Long getVersion() {
        return _version;
    }

    public void setVersion(final Long version) {
        _version = version;
    }

    public Timestamp getCreatedAt() {
        return _createdAt;
    }

    public void setCreatedAt(final Timestamp value) {
        _createdAt = value;
    }

    public Timestamp getUpdatedAt() {
        return _updatedAt;
    }

    public void setUpdatedAt(final Timestamp value) {
        _updatedAt = value;
    }

    public UUID getUuid() {
        return _uuid;
    }

    public void setUuid(final UUID value) {
        _uuid = value;
    }

    public String getName() {
        return _name;
    }

    public void setName(final String value) {
        _name = value;
    }

    public String getCluster() {
        return _cluster;
    }

    public void setCluster(final String value) {
        _cluster = value;
    }

    public String getService() {
        return _service;
    }

    public void setService(final String value) {
        _service = value;
    }

    public Context getContext() {
        return _context;
    }

    public void setContext(final Context value) {
        _context = value;
    }

    public String getMetric() {
        return _metric;
    }

    public void setMetric(final String value) {
        _metric = value;
    }

    public String getStatistic() {
        return _statistic;
    }

    public void setStatistic(final String value) {
        _statistic = value;
    }

    public int getPeriod() {
        return _periodInSeconds;
    }

    public void setPeriod(final int value) {
        _periodInSeconds = value;
    }

    public Operator getOperator() {
        return _operator;
    }

    public void setOperator(final Operator value) {
        _operator = value;
    }

    public double getQuantityValue() {
        return _quantityValue;
    }

    public void setQuantityValue(final double value) {
        _quantityValue = value;
    }

    public String getQuantityUnit() {
        return _quantityUnit;
    }

    public void setQuantityUnit(final String value) {
        _quantityUnit = value;
    }

    public NagiosExtension getNagiosExtension() {
        return _nagiosExtension;
    }

    public void setNagiosExtension(final NagiosExtension value) {
        _nagiosExtension = value;
    }
}
