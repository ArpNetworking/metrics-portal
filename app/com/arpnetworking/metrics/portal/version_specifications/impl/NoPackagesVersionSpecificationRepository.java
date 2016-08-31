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
package com.arpnetworking.metrics.portal.version_specifications.impl;

import com.arpnetworking.metrics.portal.version_specifications.VersionSpecificationRepository;
import models.internal.QueryResult;
import models.internal.VersionSet;
import models.internal.VersionSetLookupResult;
import models.internal.VersionSpecification;
import models.internal.impl.DefaultQueryResult;
import models.internal.impl.DefaultVersionSet;
import models.internal.impl.DefaultVersionSetLookupResult;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Matthew Hayter (mhayter at groupon dot com)
 * @since 1.0.0
 */
public class NoPackagesVersionSpecificationRepository implements VersionSpecificationRepository {

    private DefaultVersionSet _versionSet;

    @Override
    public void open() {
        _versionSet = new DefaultVersionSet.Builder()
                .setPackageVersions(Collections.emptyList())
                .setUuid(_versionSetUuid)
                .setVersion("1")
                .build();
    }

    @Override
    public void close() {
    }

    @Override
    public VersionSetLookupResult lookupVersionSetByHostAttributes(
            final Map<String, String> attributes,
            final Optional<Instant> ifModifiedSince) {
        return new DefaultVersionSetLookupResult(Optional.of(_versionSet), false, false, Optional.of(_modifiedTime));
    }

    @Override
    public Optional<VersionSet> getVersionSet(final UUID uuid) {
        if (uuid.equals(_versionSetUuid)) {
            return Optional.of(_versionSet);
        }
        return Optional.empty();
    }

    @Override
    public List<VersionSet> getVersionSets(final int offset, final int limit) {
        return Collections.singletonList(_versionSet);
    }

    @Override
    public void addOrUpdateVersionSet(final VersionSet versionSet) {
    }

    @Override
    public void addOrUpdateVersionSpecification(final VersionSpecification versionSpecification) {
    }

    @Override
    public void deleteVersionSpecification(final UUID uuid) {
    }

    @Override
    public Optional<VersionSpecification> getVersionSpecification(final UUID uuid) {
        return Optional.empty();
    }

    @Override
    public QueryResult<VersionSpecification> getVersionSpecifications(final int offset, final int limit) {
        return new DefaultQueryResult<>(Collections.emptyList(), 0);
    }

    private final UUID _versionSetUuid = UUID.randomUUID();
    private final Instant _modifiedTime = Instant.now();
}
