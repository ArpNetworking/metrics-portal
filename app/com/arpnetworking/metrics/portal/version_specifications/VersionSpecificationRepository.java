/**
 * Copyright 2016 Groupon.com
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
package com.arpnetworking.metrics.portal.version_specifications;

import models.internal.QueryResult;
import models.internal.VersionSet;
import models.internal.VersionSetLookupResult;
import models.internal.VersionSpecification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface for repository of version specifications.
 *
 * @author Matthew Hayter (mhayter at groupon dot com)
 */
public interface VersionSpecificationRepository {

    /**
     * Find the version set that corresponds to the set of host attributes by considering the list of version specifications.
     *
     * @param attributes The set of host attributes.
     * @param ifModifiedSince An optional <code>DateTime</code> indicating that the result is not needed if the age
     * of the underlying data is equal to or older than this moment. Leave this absent to disregard
     * the age of the data.
     * @return Either a LookupResultError indicating no new content or no <code>VersionSet</code> found, or the
     * corresponding version set, which contains a package-version list. If the ifModifiedSince parameter is left
     * absent, the value will not be <code>LookupResultError.NOT_MODIFIED</code>.
     */
    VersionSetLookupResult lookupVersionSetByHostAttributes(Map<String, String> attributes, final Optional<Instant> ifModifiedSince);

    /**
     * Lookup a version set by UUID.
     *
     * @param uuid The uuid of the version set.
     * @return The version set.
     */
    Optional<VersionSet> getVersionSet(UUID uuid);

    /**
     * Find the full list of version sets.
     *
     * @param offset The number of version specifications to skip in the response
     * @param limit The maximum number of version specifications to return.
     * @return The list of version sets.
     */
    List<VersionSet> getVersionSets(int offset, int limit);

    /**
     * Create or update a version set. The UUID of the version set defines whether the operation will create or update a version set.
     *
     * @param versionSet The new/updated version set.
     */
    void addOrUpdateVersionSet(VersionSet versionSet);

    /**
     * Create or update a version set. The existence of the UUID of the version specification defines whether the operation
     * will create or update a version specification. The linked Version Set must exist, and will only be referenced to obtain the
     * <code>VersionSet</code> UUID.
     *
     * @param versionSpecification The version specification to be updated or created.
     */
    void addOrUpdateVersionSpecification(VersionSpecification versionSpecification);

    /**
     * Delete a version specification.
     *
     * @param uuid The uuid.
     */
    void deleteVersionSpecification(UUID uuid);

    /**
     * Get version specification by UUID.
     *
     * @param uuid The uuid.
     * @return The version specification.
     */
    Optional<VersionSpecification> getVersionSpecification(UUID uuid);

    /**
     * Get a list of all version specifications.
     *
     * @param offset The number of version specifications to skip in the response
     * @param limit The maximum number of version specifications to return.
     * @return The list of version specifications.
     */
    QueryResult<VersionSpecification> getVersionSpecifications(int offset, int limit);
}
