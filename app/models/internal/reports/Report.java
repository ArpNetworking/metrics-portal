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
import models.internal.scheduling.Job;

/**
 * Internal model for a Report.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public interface Report extends Job<Report.Result> {
    /**
     * Get the name of this report.
     *
     * @return The name of this report.
     */
    String getName();

    /**
     * Get the source used to generate this report.
     *
     * @return The the source for this report.
     */
    ReportSource getSource();

    /**
     * Get the recipient groups that will receive this report.
     *
     * @return The recipient groups that will receive this report.
     */
    ImmutableCollection<RecipientGroup> getRecipientGroups();

    /**
     * Internal model for a result created from a report.
     */
    interface Result {}
}
