/*
 * Copyright 2016 Smartsheet.com
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
import com.google.common.base.Objects;
import models.internal.Organization;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.util.UUID;

/**
 * Default internal model implementation for an organization.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
@Loggable
public final class DefaultOrganization implements Organization {

    @Override
    public UUID getId() {
        return _id;
    }

    @Override
    public boolean equals(final Object other) {
        if (other == this) {
            return true;
        }

        if (!(other instanceof DefaultOrganization)) {
            return false;
        }

        final DefaultOrganization otherOrg = (DefaultOrganization) other;
        return Objects.equal(_id, otherOrg._id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(_id);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", Integer.toHexString(System.identityHashCode(this)))
                .add("class", this.getClass())
                .add("Id", _id)
                .toString();
    }

    private DefaultOrganization(final Builder builder) {
        _id = builder._id;
    }

    private final UUID _id;

    /**
     * Implementation of builder pattern for {@link DefaultOrganization}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends OvalBuilder<Organization> {

        /**
         * Public constructor.
         */
        public Builder() {
            super(DefaultOrganization::new);
        }

        /**
         * The id. Cannot be null or empty.
         *
         * @param value The id.
         * @return This instance of {@link Builder}.
         */
        public Builder setId(final UUID value) {
            _id = value;
            return this;
        }

        @NotNull
        @NotEmpty
        private UUID _id;
    }
}
