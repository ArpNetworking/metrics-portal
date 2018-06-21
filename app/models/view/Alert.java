/*
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

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.logback.annotations.Loggable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.MoreObjects;
import models.internal.Organization;
import models.internal.impl.DefaultAlert;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;
import org.joda.time.Period;

import java.util.UUID;

/**
 * View model of <code>Alert</code>.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
@Loggable
public final class Alert {

    public UUID getId() {
        return _id;
    }

    public String getName() {
        return _name;
    }

    public String getQuery() {
        return _query;
    }

    public Period getCheckInterval() {
        return _checkInterval;
    }

    public String getComment() {
        return _comment;
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
                .add("Period", _checkInterval)
                .toString();
    }

    /**
     * Converts a view model to an internal model.
     *
     * @param organization organization the alert belongs to
     * @param objectMapper object mapper to convert some values
     * @return a new internal model
     */
    public models.internal.Alert toInternal(
            final Organization organization,
        final ObjectMapper objectMapper) {
        final DefaultAlert.Builder alertBuilder = new DefaultAlert.Builder()
                .setId(_id)
                .setName(_name)
                .setQuery(_query)
                .setCheckInterval(_checkInterval)
                .setComment(_comment)
                .setOrganization(organization);

        return alertBuilder.build();
    }

    /**
     * Converts an internal model to a view model.
     *
     * @param alert the alert
     * @return a new view model
     */
    public static Alert fromInternal(final models.internal.Alert alert) {
        return new Alert.Builder()
                .setId(alert.getId())
                .setName(alert.getName())
                .setQuery(alert.getQuery())
                .setCheckInterval(alert.getCheckInterval())
                .setComment(alert.getComment())
                .build();
    }

    private final UUID _id;
    private final String _name;
    private final String _query;
    private final Period _checkInterval;
    private final String _comment;

    private Alert(final Builder builder) {
        _id = builder._id;
        _name = builder._name;
        _query = builder._query;
        _checkInterval = builder._checkInterval;
        _comment = builder._comment;
    }

    /**
     * Implementation of the builder pattern for {@link Alert}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends OvalBuilder<Alert> {

        /**
         * Public constructor.
         */
        public Builder() {
            super(Alert::new);
        }

        /**
         * Sets the id. Required.
         *
         * @param value the id
         * @return this {@link Builder}
         */
        public Builder setId(final UUID value) {
            _id = value;
            return this;
        }

        /**
         * Sets the name. Required.
         *
         * @param value the name of the query
         * @return this {@link Builder}
         */
        public Builder setName(final String value) {
            _name = value;
            return this;
        }

        /**
         * Sets the query. Required.
         *
         * @param value the query
         * @return this {@link Builder}
         */
        public Builder setQuery(final String value) {
            _query = value;
            return this;
        }

        /**
         * Sets the check interval. Optional. Defaults PT1M. Cannot be null.
         *
         * @param value the check interval
         * @return this {@link Builder}
         */
        public Builder setCheckInterval(final Period value) {
            _checkInterval = value;
            return this;
        }

        /**
         * Sets the comment. Optional. Defaults empty. Cannot be null.
         *
         * @param value the comment
         * @return this {@link Builder}
         */
        public Builder setComment(final String value) {
            _comment = value;
            return this;
        }

        @NotNull
        private UUID _id;
        @NotNull
        @NotEmpty
        private String _name;
        @NotNull
        @NotEmpty
        private String _query;
        @NotNull
        private Period _checkInterval = Period.minutes(1);
        @NotNull
        private String _comment = "";
    }
}
