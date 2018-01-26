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
package models.view;

import com.arpnetworking.logback.annotations.Loggable;
import com.arpnetworking.metrics.portal.notifications.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import models.internal.NagiosExtension;
import models.internal.Organization;
import models.internal.impl.DefaultAlert;
import org.joda.time.Minutes;
import org.joda.time.Period;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * View model of <code>Alert</code>. Play view models are mutable.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
@Loggable
public final class Alert {

    public void setId(final String value) {
        _id = value;
    }

    public String getId() {
        return _id;
    }

    public void setName(final String value) {
        _name = value;
    }

    public String getName() {
        return _name;
    }

    public String getQuery() {
        return _query;
    }

    public void setQuery(final String query) {
        _query = query;
    }

    public void setPeriod(final String value) {
        _period = value;
    }

    public String getPeriod() {
        return _period;
    }

    public void setExtensions(final ImmutableMap<String, Object> extensions) {
        _extensions = extensions;
    }

    public ImmutableMap<String, Object> getExtensions() {
        return _extensions;
    }

    public UUID getNotificationGroupId() {
        return _notificationGroupId;
    }

    public void setNotificationGroupId(final UUID notificationGroupId) {
        _notificationGroupId = notificationGroupId;
    }

    public String getComment() {
        return _comment;
    }

    public void setComment(final String comment) {
        _comment = comment;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", Integer.toHexString(System.identityHashCode(this)))
                .add("class", this.getClass())
                .add("Id", _id)
                .add("Name", _name)
                .add("Comment", _comment)
                .add("Query", _query)
                .add("Period", _period)
                .add("Extensions", _extensions)
                .toString();
    }

    /**
     * Converts a view model to an internal model.
     *
     * @param organization organization the alert belongs to
     * @param notificationRepository notification repository to resolve notification groups
     * @param objectMapper object mapper to convert some values
     * @return a new internal model
     */
    public models.internal.Alert toInternal(
            final Organization organization,
        final NotificationRepository notificationRepository,
        final ObjectMapper objectMapper) {
        final DefaultAlert.Builder alertBuilder = new DefaultAlert.Builder()
                .setName(_name)
                .setQuery(_query)
                .setOrganization(organization);
        if (_id != null) {
            alertBuilder.setId(UUID.fromString(_id));
        }
        if (_period != null) {
            // Minimum period supported is 1 minute
            Period period = Period.parse(_period);
            if (period.toStandardMinutes().isLessThan(Minutes.ONE)) {
                period = Period.minutes(1);
            }
            alertBuilder.setPeriod(period);

        }
        if (_comment != null) {
            alertBuilder.setComment(_comment);
        }
        if (_extensions != null) {
            alertBuilder.setNagiosExtension(toInternalNagiosExtension(_extensions, objectMapper).orElse(null));
        }
        if (_notificationGroupId != null) {
            alertBuilder.setNotificationGroup(
                    notificationRepository.getNotificationGroup(
                            _notificationGroupId,
                            organization)
                            .orElse(null));
        }
        return alertBuilder.build();
    }

    private Optional<NagiosExtension> toInternalNagiosExtension(final Map<String, Object> extensionsMap, final ObjectMapper objectMapper) {
        try {
            return Optional.of(
                    objectMapper
                            .convertValue(extensionsMap, NagiosExtension.Builder.class)
                            .build());
            // CHECKSTYLE.OFF: IllegalCatch - Assume there is no Nagios data on build failure.
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            return Optional.empty();
        }
    }

    private String _id;
    private String _name;
    private String _query;
    private String _comment;
    private String _period;
    private ImmutableMap<String, Object> _extensions;
    private UUID _notificationGroupId;
}
