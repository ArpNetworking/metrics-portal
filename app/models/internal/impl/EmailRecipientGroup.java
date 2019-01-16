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
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import models.internal.reports.RecipientGroup;
import models.internal.reports.ReportFormat;
import net.sf.oval.constraint.EmailCheck;
import net.sf.oval.constraint.MinSize;
import net.sf.oval.constraint.NotBlank;
import net.sf.oval.constraint.NotNull;
import net.sf.oval.constraint.ValidateWithMethod;

import java.util.Objects;
import java.util.UUID;

/**
 * A {@code RecipientGroup} that consists of a set of email addresses.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class EmailRecipientGroup implements RecipientGroup {
    private final UUID _id;
    private final String _name;
    private final ImmutableSet<String> _emails;
    private final ImmutableList<ReportFormat> _formats;

    private EmailRecipientGroup(final Builder builder) {
        _id = builder._id;
        _name = builder._name;
        _emails = builder._emails;
        _formats = builder._formats;
    }

    @Override
    public UUID getId() {
        return _id;
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public ImmutableSet<String> getMembers() {
        return _emails;
    }

    @Override
    public ImmutableList<ReportFormat> getFormats() {
        return _formats;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EmailRecipientGroup that = (EmailRecipientGroup) o;
        return Objects.equals(_id, that._id)
                && Objects.equals(_name, that._name)
                && Objects.equals(_emails, that._emails)
                && Objects.equals(_formats, that._formats);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_id, _name, _emails, _formats);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", _id)
                .add("name", _name)
                .add("emails", _emails)
                .add("formats", _formats)
                .toString();
    }

    /**
     * Builder implementation that constructs {@code EmailRecipientGroup}.
     */
    public static final class Builder extends OvalBuilder<EmailRecipientGroup> {
        private static final EmailCheck EMAIL_CHECK = new EmailCheck();
        @NotNull
        private UUID _id;
        @NotNull
        @NotBlank
        private String _name;
        @ValidateWithMethod(
                methodName = "validateEmails",
                parameterType = ImmutableSet.class
        )
        private ImmutableSet<String> _emails;
        @NotNull
        @MinSize(value = 1)
        private ImmutableList<ReportFormat> _formats;

        /**
         * Public constructor.
         */
        public Builder() {
            super(EmailRecipientGroup::new);
        }

        /**
         * The group id. Required. Cannot be null or empty.
         *
         * @param id The id of the group.
         * @return This instance of {@code Builder}.
         */
        public Builder setId(final UUID id) {
            _id = id;
            return this;
        }

        /**
         * The group name. Required. Cannot be null or empty.
         *
         * @param name The name of the group.
         * @return This instance of {@code Builder}.
         */
        public Builder setName(final String name) {
            _name = name;
            return this;
        }

        /**
         * The email group members. Required. Cannot be null or empty.
         *
         * @param emails The emails for the group.
         * @return This instance of {@code Builder}.
         */
        public Builder setEmails(final ImmutableSet<String> emails) {
            _emails = emails;
            return this;
        }

        /**
         * The group formats. Required. Cannot be null or empty.
         *
         * @param formats The formats for the group.
         * @return This instance of {@code Builder}.
         */
        public Builder setFormats(final ImmutableList<ReportFormat> formats) {
            _formats = formats;
            return this;
        }

        @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "Called by OvalBuilder generated validation code.")
        private boolean validateEmails(final ImmutableSet<?> emails) {
            // TODO(cbriones): Replace this method and static EmailCheck with the appropriate field annotation once supported.
            //
            // An @Email annotation would only apply to the ImmutableSet, not its members. This is because OvalBuilder doesn't
            // support the appliesTo argument in its validation processor.
            //
            // See: https://github.com/ArpNetworking/commons/issues/79
            return emails
                    .stream()
                    .allMatch(e -> EMAIL_CHECK.isSatisfied(this, e, null, null));
        }
    }
}
