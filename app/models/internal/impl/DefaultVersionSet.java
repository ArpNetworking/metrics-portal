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

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import models.internal.PackageVersion;
import models.internal.VersionSet;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Default internal model implementation for a version set.
 *
 * @author Matthew Hayter (mhayter at groupon dot com)
 */
@Loggable
public final class DefaultVersionSet implements VersionSet {

    private final UUID _uuid;
    private final String _version;
    private final List<PackageVersion> _packageVersions;

    /**
     * {@inheritDoc}
     */
    @Override
    public UUID getUuid() {
        return _uuid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getVersion() {
        return _version;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PackageVersion> getPackageVersions() {
        return _packageVersions;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof DefaultVersionSet)) {
            return false;
        }

        final DefaultVersionSet otherDefaultVersionSet = (DefaultVersionSet) other;
        return Objects.equal(_uuid, otherDefaultVersionSet._uuid)
                && Objects.equal(_version, otherDefaultVersionSet._version)
                && Objects.equal(_packageVersions, otherDefaultVersionSet._packageVersions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(_uuid, _version, _packageVersions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("Uuid", _uuid)
                .add("Version", _version)
                .add("PackageVersions", _packageVersions)
                .toString();
    }

    private DefaultVersionSet(final Builder builder) {
        _uuid = builder._uuid;
        _version = builder._version;
        _packageVersions = builder._packageVersions;
    }

    /**
     * Implementation of builder pattern for <code>DefaultVersionSet</code>.
     *
     * @author Matthew Hayter (mhayter at groupon dot com)
     */
    public static final class Builder extends OvalBuilder<DefaultVersionSet> {

        /**
         * Public constructor.
         */
        public Builder() {
            super(DefaultVersionSet::new);
        }

        /**
         * The Uuid.
         *
         * @param value The UUID.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setUuid(final UUID value) {
            _uuid = value;
            return this;
        }

        /**
         * The version.
         *
         * @param value The version.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setVersion(final String value) {
            _version = value;
            return this;
        }

        /**
         * The package versions associated with the <code>VersionSet</code>.
         *
         * @param value The package version.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setPackageVersions(final List<PackageVersion> value) {
            _packageVersions = value;
            return this;
        }

        @NotNull
        private List<PackageVersion> _packageVersions;

        @NotNull
        @NotEmpty
        private String _version;

        @NotNull
        private UUID _uuid;
    }
}
