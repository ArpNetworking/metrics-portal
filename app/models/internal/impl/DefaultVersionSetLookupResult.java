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
package models.internal.impl;

import models.internal.VersionSet;
import models.internal.VersionSetLookupResult;

import java.time.Instant;
import java.util.Optional;

/**
 * Default implementation of <code>VersionSetLookupResult</code>.
 *
 * @author Matthew Hayter (mhayter at groupon dot com)
 * @since 1.0.0
 */
public class DefaultVersionSetLookupResult implements VersionSetLookupResult {

    /**
     * Public constructor.
     *
     * @param versionSet The <code>VersionSet</code>.
     * @param isNotModified True if the VersionSet was not modified given the context of the lookup.
     * @param isNotFound True if the <code>VersionSet</code> was not found.
     * @param lastModified <code>Instant</code> of last modification time.
     */
    public DefaultVersionSetLookupResult(
            final Optional<VersionSet> versionSet,
            final boolean isNotModified,
            final boolean isNotFound,
            final Optional<Instant> lastModified) {
        _versionSet = versionSet;
        _isNotModified = isNotModified;
        _isNotFound = isNotFound;
        _lastModified = lastModified;
    }

    @Override
    public Optional<VersionSet> getVersionSet() {
        return _versionSet;
    }

    @Override
    public boolean isNotModified() {
        return _isNotModified;
    }

    @Override
    public boolean isNotFound() {
        return _isNotFound;
    }

    @Override
    public Optional<Instant> getLastModified() {
        return _lastModified;
    }

    private final Optional<VersionSet> _versionSet;
    private final boolean _isNotModified;
    private final boolean _isNotFound;
    private final Optional<Instant> _lastModified;
}
