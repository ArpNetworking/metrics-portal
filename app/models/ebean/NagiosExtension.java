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

import io.ebean.annotation.CreatedTimestamp;
import io.ebean.annotation.UpdatedTimestamp;

import java.sql.Timestamp;
import javax.persistence.CascadeType;
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
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@Table(name = "nagios_extensions", schema = "portal")
public class NagiosExtension {

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

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "alert_id")
    private Alert alert;

    @Column(name = "severity")
    private String severity;

    @Column(name = "notify")
    private String notify;

    @Column(name = "max_check_attempts")
    private int maxCheckAttempts;

    @Column(name = "freshness_threshold_in_seconds")
    private long freshnessThresholdInSeconds;

    public Alert getAlert() {
        return alert;
    }

    public void setAlert(final Alert value) {
        alert = value;
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

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(final String value) {
        severity = value;
    }

    public String getNotify() {
        return notify;
    }

    public void setNotify(final String value) {
        notify = value;
    }

    public int getMaxCheckAttempts() {
        return maxCheckAttempts;
    }

    public void setMaxCheckAttempts(final int value) {
        maxCheckAttempts = value;
    }

    public long getFreshnessThreshold() {
        return freshnessThresholdInSeconds;
    }

    public void setFreshnessThreshold(final long value) {
        freshnessThresholdInSeconds = value;
    }

    /**
     * Converts this model into an {@link models.internal.NagiosExtension}.
     *
     * @return a new internal model
     */
    public models.internal.NagiosExtension toInternal() {
        return new models.internal.NagiosExtension.Builder()
                .setSeverity(getSeverity())
                .setNotify(getNotify())
                .setMaxCheckAttempts(getMaxCheckAttempts())
                .setFreshnessThresholdInSeconds(getFreshnessThreshold())
                .build();
    }

    /**
     * Creates a {@link NagiosExtension} from a {@link models.internal.NagiosExtension}.
     * @param internalExtension the {@link models.internal.NagiosExtension} source
     * @return a new {@link NagiosExtension}
     */
    public static NagiosExtension fromInternal(final models.internal.NagiosExtension internalExtension) {
        if (internalExtension == null) {
            return null;
        }
        final NagiosExtension extension = new NagiosExtension();
        extension.setSeverity(internalExtension.getSeverity());
        extension.setNotify(internalExtension.getNotify());
        extension.setMaxCheckAttempts(internalExtension.getMaxCheckAttempts());
        extension.setFreshnessThreshold(internalExtension.getFreshnessThreshold().getStandardSeconds());
        return extension;
    }
}
// CHECKSTYLE.ON: MemberNameCheck
