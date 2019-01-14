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
import models.internal.reports.RecipientGroup;
import models.internal.reports.ReportFormat;
import net.sf.oval.constraint.MinSize;
import net.sf.oval.constraint.NotBlank;
import net.sf.oval.constraint.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A {@code RecipientGroup} that consists of a set of email addresses.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class EmailRecipientGroup implements RecipientGroup {
    private final UUID _id;
    private final String _name;
    private final Set<String> _emails;
    private final List<ReportFormat> _formats;

    private EmailRecipientGroup(final Builder builder) {
        _id = builder._id;
        _name = builder._name;
        _emails = Collections.unmodifiableSet(builder._emails);
        _formats = Collections.unmodifiableList(builder._formats);
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
    public Collection<String> getMembers() {
        return _emails;
    }

    @Override
    public Collection<ReportFormat> getFormats() {
        return _formats;
    }

    /**
     * Builder implementation that constructs {@code EmailRecipientGroup}.
     */
    public static final class Builder extends OvalBuilder<EmailRecipientGroup> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(EmailRecipientGroup::new);
        }

        /**
         * The group id. Required. Cannot be null or empty.
         * @param id The id of the group.
         * @return This instance of {@code Builder}.
         */
        public Builder setId(final UUID id) {
            _id = id;
            return this;
        }

        /**
         * The group name. Required. Cannot be null or empty.
         * @param name The name of the group.
         * @return This instance of {@code Builder}.
         */
        public Builder setName(final String name) {
            _name = name;
            return this;
        }

        /**
         * The email group members. Required. Cannot be null or empty.
         * @param emails The emails for the group.
         * @return This instance of {@code Builder}.
         */
        public Builder setEmails(final Set<String> emails) {
            _emails = emails;
            return this;
        }

        /**
         * The group formats. Required. Cannot be null or empty.
         * @param formats The formats for the group.
         * @return This instance of {@code Builder}.
         */
        public Builder setFormats(final List<ReportFormat> formats) {
            _formats = formats;
            return this;
        }

        @NotNull
        private UUID _id;
        @NotNull
        @NotBlank
        private String _name;
        @NotNull
        @MinSize(value = 1)
        private Set<String> _emails;
        @NotNull
        @MinSize(value = 1)
        private List<ReportFormat> _formats;
    }
}
