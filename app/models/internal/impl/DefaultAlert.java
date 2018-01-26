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
package models.internal.impl;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import models.internal.Alert;
import models.internal.NagiosExtension;
import models.internal.NotificationGroup;
import models.internal.Organization;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;
import org.joda.time.Period;

import java.util.Objects;
import java.util.UUID;

/**
 * Default internal model implementation for an alert.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
@Loggable
public final class DefaultAlert implements Alert {

    @Override
    public UUID getId() {
        return _id;
    }

    @Override
    public Organization getOrganization() {
        return _organization;
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public String getQuery() {
        return _query;
    }

    @Override
    public String getComment() {
        return _comment;
    }

    @Override
    public Period getPeriod() {
        return _period;
    }

    @Override
    public models.view.Alert toView() {
        final models.view.Alert viewAlert = new models.view.Alert();
        viewAlert.setExtensions(mergeExtensions(_nagiosExtension));
        viewAlert.setId(_id.toString());
        viewAlert.setName(_name);
        viewAlert.setQuery(_query);
        viewAlert.setPeriod(_period.toString());
        viewAlert.setComment(_comment);
        if (_notificationGroup != null) {
            viewAlert.setNotificationGroupId(_notificationGroup.getId());
        }
        return viewAlert;
    }

    private ImmutableMap<String, Object> mergeExtensions(final NagiosExtension nagiosExtension) {
        final ImmutableMap.Builder<String, Object> nagiosMapBuilder = ImmutableMap.builder();
        if (nagiosExtension != null) {
            nagiosMapBuilder.put(NAGIOS_EXTENSION_SEVERITY_KEY, nagiosExtension.getSeverity());
            nagiosMapBuilder.put(NAGIOS_EXTENSION_NOTIFY_KEY, nagiosExtension.getNotify());
            nagiosMapBuilder.put(NAGIOS_EXTENSION_MAX_CHECK_ATTEMPTS_KEY, nagiosExtension.getMaxCheckAttempts());
            nagiosMapBuilder.put(NAGIOS_EXTENSION_FRESHNESS_THRESHOLD_KEY, nagiosExtension.getFreshnessThreshold().getStandardSeconds());
        }
        return nagiosMapBuilder.build();
    }


    @Override
    public NagiosExtension getNagiosExtension() {
        return _nagiosExtension;
    }

    @Override
    public NotificationGroup getNotificationGroup() {
        return _notificationGroup;
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
                .add("NagiosExtensions", _nagiosExtension)
                .add("NotificationGroup", _notificationGroup)
                .toString();
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof DefaultAlert)) {
            return false;
        }

        final DefaultAlert otherAlert = (DefaultAlert) other;
        return Objects.equals(_id, otherAlert._id)
                && Objects.equals(_name, otherAlert._name)
                && Objects.equals(_query, otherAlert._query)
                && Objects.equals(_comment, otherAlert._comment)
                && Objects.equals(_period.normalizedStandard(), otherAlert._period.normalizedStandard())
                && Objects.equals(_notificationGroup, otherAlert._notificationGroup)
                && Objects.equals(_nagiosExtension, otherAlert._nagiosExtension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                _id,
                _query,
                _name,
                _comment,
                _period,
                _nagiosExtension,
                _notificationGroup);
    }

    private DefaultAlert(final Builder builder) {
        _id = builder._id;
        _organization =  builder._organization;
        _name = builder._name;
        _query = builder._query;
        _period = builder._period;
        _nagiosExtension = builder._nagiosExtension;
        _notificationGroup = builder._notificationGroup;
        _comment = builder._comment;
    }

    private final UUID _id;
    private final Organization _organization;
    private final String _name;
    private final String _query;
    private final String _comment;
    private final Period _period;
    private final NagiosExtension _nagiosExtension;
    private final NotificationGroup _notificationGroup;

    private static final String NAGIOS_EXTENSION_SEVERITY_KEY = "severity";
    private static final String NAGIOS_EXTENSION_NOTIFY_KEY = "notify";
    private static final String NAGIOS_EXTENSION_MAX_CHECK_ATTEMPTS_KEY = "max_check_attempts";
    private static final String NAGIOS_EXTENSION_FRESHNESS_THRESHOLD_KEY = "freshness_threshold";

    /**
     * Builder implementation for <code>DefaultAlert</code>.
     */
    public static final class Builder extends OvalBuilder<DefaultAlert> {

        /**
         * Public constructor.
         */
        public Builder() {
            super(DefaultAlert::new);
        }

        /**
         * The identifier. Required. Cannot be null.
         *
         * @param value The identifier.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setId(final UUID value) {
            _id = value;
            return this;
        }

        /**
         * A supplier to provide the Organization. Required. Cannot be null.
         *
         * @param value The organization supplier.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setOrganization(final Organization value) {
            _organization = value;
            return this;
        }

        /**
         * The name. Required. Cannot be null or empty.
         *
         * @param value The name.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setName(final String value) {
            _name = value;
            return this;
        }

        /**
         * The query to execute. Required. Cannot be null or empty.
         *
         * @param value The query to execute.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setQuery(final String value) {
            _query = value;
            return this;
        }

        /**
         * Comment about the alert. Optional. Cannot be null. Defaults to empty.
         *
         * @param value Alert comment
         * @return This instance of <code>Builder</code>.
         */
        public Builder setComment(final String value) {
            _comment = value;
            return this;
        }

        /**
         * The period. Required. Cannot be null or empty.
         *
         * @param value The period.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setPeriod(final Period value) {
            _period = value;
            return this;
        }

        /**
         * The nagios specific extensions.
         *
         * @param value The extensions.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setNagiosExtension(final NagiosExtension value) {
            _nagiosExtension = value;
            return this;
        }

        /**
         * The notification group to notify.
         *
         * @param value The notification group.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setNotificationGroup(final NotificationGroup value) {
            _notificationGroup = value;
            return this;
        }

        @NotNull
        private UUID _id;
        @NotNull
        private Organization _organization;
        @NotNull
        @NotEmpty
        private String _name;
        @NotNull
        @NotEmpty
        private String _query;
        @NotNull
        private String _comment = "";
        @NotNull
        private Period _period;
        private NotificationGroup _notificationGroup;
        private NagiosExtension _nagiosExtension;
    }
}
