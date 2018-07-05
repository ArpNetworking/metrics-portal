/*
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
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

/**
 * Default internal model implementation for a package version
 *.
 * @author Matthew Hayter (mhayter at groupon dot com)
 */
@Loggable
public final class DefaultPackageVersion implements PackageVersion {

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public String getVersion() {
        return _version;
    }

    @Override
    public String getUri() {
        return _uri;
    }

    @Override
    public boolean equals(final Object other) {
        if (other == this) {
            return true;
        }

        if (!(other instanceof DefaultPackageVersion)) {
            return false;
        }

        final DefaultPackageVersion otherPackageVersion = (DefaultPackageVersion) other;
        return Objects.equal(_name, otherPackageVersion._name)
                && Objects.equal(_uri, otherPackageVersion._uri)
                && Objects.equal(_version, otherPackageVersion._version);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(_name, _uri, _version);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("Uri", _uri)
                .add("Version", _version)
                .add("Name", _name)
                .toString();
    }

    private DefaultPackageVersion(final Builder builder) {
        _uri = builder._uri;
        _version = builder._version;
        _name = builder._name;
    }

    private final String _uri;
    private final String _version;
    private final String _name;

    /**
     * Implementation of builder pattern for <code>DefaultPackageVersion</code>.
     *
     * @author Matthew Hayter (mhayter at groupon dot com)
     */
    public static final class Builder extends OvalBuilder<DefaultPackageVersion> {

        /**
         * Public constructor.
         */
        public Builder() {
            super(DefaultPackageVersion::new);
        }

        /**
         * The name.
         *
         * @param value The name.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setName(final String value) {
            _name = value;
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
         * The uri.
         *
         * @param value The uri.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setUri(final String value) {
            _uri = value;
            return this;
        }

        @NotNull
        @NotEmpty
        private String _uri;

        @NotNull
        @NotEmpty
        private String _version;

        @NotNull
        @NotEmpty
        private String _name;
        
    }
}
