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

package com.arpnetworking.metrics.portal.scheduling;

import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.scheduling.Job;

import java.util.Optional;

// CHECKSTYLE.OFF: JavadocTypeCheck - Checkstyle does not recognize implSpec.
/**
 * A query against a {@link JobRepository}.
 *
 * @implSpec This should be kept in sync with {@link JobQuery}.
 *
 * @param <T> The result type of the Jobs returned by this query.
 * @author Christian Briones (cbriones at dropbox dot com)
 */
// CHECKSTYLE.ON: JavadocTypeCheck
public interface JobQuery<T> {
    /**
     * Execute the query and return the results.
     *
     * @return The results of the query as a {@code QueryResult<Job<T>>} instance.
     */
    QueryResult<Job<T>> execute();

    /**
     * The maximum number of jobs to return.  Optional. Default is 1000.
     *
     * @param limit The maximum number of jobs to return.
     * @return This instance of {@code JobQuery}
     */
    JobQuery<T> limit(int limit);

    /**
     * The offset into the result set. Optional. Default is not set.
     *
     * @param offset The offset into the result set.
     * @return This instance of {@code JobQuery}
     */
    JobQuery<T> offset(int offset);

    /**
     * Accessor for the organization.
     *
     * @return The organization.
     */
    Organization getOrganization();

    /**
     * Accessor for the limit.
     *
     * @return The limit.
     */
    int getLimit();

    /**
     * Accessor for the offset.
     *
     * @return The offset, if any, otherwise {@link Optional#empty()}
     */
    Optional<Integer> getOffset();
}
