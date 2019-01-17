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

package models.internal.reports;

import com.google.common.collect.ImmutableCollection;

import java.util.UUID;

/**
 * Internal model representing recipients for a {@link Report}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public interface RecipientGroup {

    /**
     * Get the id for this group.
     *
     * @return the id for this group.
     */
    UUID getId();

    /**
     * Get the members of this group.
     *
     * @return A collection containing the group members.
     */
    ImmutableCollection<String> getMembers();

    /**
     * Get the formats requested by this group.
     *
     * @return A collection of formats requested by this group.
     */
    ImmutableCollection<ReportFormat> getFormats();

    /**
     * Get the name of this group.
     *
     * @return the name of this group.
     */
    String getName();
}
