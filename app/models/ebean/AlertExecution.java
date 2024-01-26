/*
 * Copyright 2019 Dropbox, Inc.
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

import com.google.common.base.Objects;
import io.ebean.annotation.DbJsonB;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import models.internal.alerts.AlertEvaluationResult;

import java.time.Instant;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * An execution event for an {@link models.internal.alerts.Alert}.
 * <p>
 * NOTE: This class is enhanced by Ebean to do things like lazy loading and
 * resolving relationships between beans. Therefore, including functionality
 * which serializes the state of the object can be dangerous (e.g. {@code toString},
 * {@code @Loggable}, etc.).
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@Table(name = "alert_executions", schema = "portal")
@IdClass(AlertExecution.Key.class)
public final class AlertExecution extends BaseExecution<AlertEvaluationResult> {
    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "organization_id")
    private Organization organization;
    @Id
    @Column(name = "alert_id")
    private UUID alertId;
    @Nullable
    @DbJsonB
    @Column(name = "result")
    private AlertEvaluationResult result;

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(final Organization value) {
        organization = value;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public void setAlertId(final UUID value) {
        alertId = value;
    }

    @Override
    public UUID getJobId() {
        return alertId;
    }

    @Override
    public void setJobId(final UUID jobId) {
        alertId = jobId;
    }

    @Override
    @Nullable
    public AlertEvaluationResult getResult() {
        return result;
    }

    @Override
    public void setResult(@Nullable final AlertEvaluationResult value) {
        result = value;
    }

    /**
     * Primary Key for a {@link AlertExecution}.
     */
    @Embeddable
    protected static final class Key {
        @Nullable
        @Column(name = "organization_id")
        private final Long organizationId;
        @Nullable
        @Column(name = "alert_id")
        private final UUID alertId;
        @Nullable
        @Column(name = "scheduled")
        private final Instant scheduled;

        /**
         * Default constructor, required by Ebean.
         */
        public Key() {
            alertId = null;
            scheduled = null;
            organizationId = null;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Key key = (Key) o;
            return Objects.equal(alertId, key.alertId)
                    && Objects.equal(scheduled, key.scheduled)
                    && Objects.equal(organizationId, key.organizationId);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(alertId, scheduled, organizationId);
        }
    }
}
// CHECKSTYLE.ON: MemberNameCheck
