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

import java.sql.Timestamp;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * Data model for extensions data in alerts.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
@Entity
@Table(name = "nagios_extensions", schema = "portal")
public class NagiosExtension extends Model {

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

    @OneToOne
    @JoinColumn(name = "id")
    private Alert _alert;

    @Column(name = "severity")
    private String _severity;

    @Column(name = "notify")
    private String _notify;

    @Column(name = "max_check_attempts")
    private int _maxCheckAttempts;

    @Column(name = "freshness_threshold_in_seconds")
    private int _freshnessThresholdInSeconds;

    public Alert getAlert() {
        return _alert;
    }

    public void setAlert(final Alert value) {
        _alert = value;
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

    public String getSeverity() {
        return _severity;
    }

    public void setSeverity(final String value) {
        _severity = value;
    }

    public String getNotify() {
        return _notify;
    }

    public void setNotify(final String value) {
        _notify = value;
    }

    public int getMaxCheckAttempts() {
        return _maxCheckAttempts;
    }

    public void setMaxCheckAttempts(final int value) {
        _maxCheckAttempts = value;
    }

    public int getFreshnessThreshold() {
        return _freshnessThresholdInSeconds;
    }

    public void setFreshnessThreshold(final int value) {
        _freshnessThresholdInSeconds = value;
    }
}
