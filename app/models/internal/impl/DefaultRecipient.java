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

package models.internal.impl;

import com.arpnetworking.commons.builder.OvalBuilder;
import models.internal.reports.Recipient;
import net.sf.oval.constraint.Email;
import net.sf.oval.constraint.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Default internal model for a recipient specified by an email address.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class DefaultRecipient implements Recipient {
    private final UUID _id;
    private final String _address;

    private DefaultRecipient(final Builder builder) {
        _id = builder._id;
        _address = builder._address;
    }

    @Override
    public UUID getId() {
        return _id;
    }

    @Override
    public String getAddress() {
        return _address;
    }

    @Override
    public <T> T accept(final Visitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultRecipient that = (DefaultRecipient) o;
        return _id.equals(that._id)
                && _address.equals(that._address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_id, _address);
    }

    /**
     * Builder implementation that constructs {@code DefaultRecipient}.
     */
    public static final class Builder extends OvalBuilder<DefaultRecipient> {
        @NotNull
        private UUID _id;
        @Email
        @NotNull
        private String _address;

        /**
         * Default constructor.
         */
        public Builder() {
            super(DefaultRecipient::new);
        }

        /**
         * Set the id. Required. Cannot be null.
         *
         * @param id the recipient id
         * @return this instance of {@code Builder}
         */
        public Builder setId(final UUID id) {
            _id = id;
            return this;
        }

        /**
         * Set the email address. Required. Cannot be null.
         *
         * @param address the email address
         * @return this instance of {@code Builder}
         */
        public Builder setAddress(final String address) {
            _address = address;
            return this;
        }
    }
}
