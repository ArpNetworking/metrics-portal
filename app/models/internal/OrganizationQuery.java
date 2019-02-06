/*
 * Copyright 2019 Dropbox Inc
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
package models.internal;

import java.util.Optional;

/**
 * Internal model interface for a organization query.
 *
 * @author Ville Koskela (vkoskela at dropbox dot com)
 */
public interface OrganizationQuery {

    /**
     * The maximum number of organizations to return. Optional. Default is 1000.
     *
     * @param limit The maximum number of organizations to return.
     * @return This instance of {@link OrganizationQuery}.
     */
    OrganizationQuery limit(int limit);

    /**
     * The offset into the result set. Optional. Default is not set.
     *
     * @param offset The offset into the result set.
     * @return This instance of {@link OrganizationQuery}.
     */
    OrganizationQuery offset(Optional<Integer> offset);

    /**
     * Accessor for the limit.
     *
     * @return The limit.
     */
    int getLimit();

    /**
     * Accessor for the offset.
     *
     * @return The offset.
     */
    Optional<Integer> getOffset();
}
