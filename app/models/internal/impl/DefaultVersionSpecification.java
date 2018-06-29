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
import models.internal.VersionSet;
import models.internal.VersionSpecification;
import models.internal.VersionSpecificationAttribute;
import net.sf.oval.constraint.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Default internal model implementation for a version specification.
 *
 * @author Matthew Hayter (mhayter at groupon dot com)
 */
@Loggable
public final class DefaultVersionSpecification implements VersionSpecification {

    @Override
    public UUID getUuid() {
        return _uuid;
    }

    @Override
    public VersionSet getVersionSet() {
        return _versionSet;
    }

    @Override
    public List<VersionSpecificationAttribute> getVersionSpecificationAttributes() {
        return _versionSpecificationAttributes;
    }

    @Override
    public long getPosition() {
        return _position;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof DefaultVersionSpecification)) {
            return false;
        }

        final DefaultVersionSpecification otherDefaultVersionSpecification = (DefaultVersionSpecification) other;
        return _position == otherDefaultVersionSpecification._position
                && Objects.equal(_uuid, otherDefaultVersionSpecification._uuid)
                && Objects.equal(_versionSet, otherDefaultVersionSpecification._versionSet)
                && Objects.equal(_versionSpecificationAttributes, otherDefaultVersionSpecification._versionSpecificationAttributes);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(_uuid, _versionSet, _versionSpecificationAttributes, _position);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("Uuid", _uuid)
                .add("VersionSet", _versionSet)
                .add("VersionSpecificationAttributes", _versionSpecificationAttributes)
                .add("Position", _position)
                .toString();
    }

    private DefaultVersionSpecification(final Builder builder) {
        _uuid = builder._uuid;
        _versionSet = builder._versionSet;
        _versionSpecificationAttributes = builder._versionSpecificationAttributes;
        _position = builder._position;
    }

    private final UUID _uuid;
    private final VersionSet _versionSet;
    private final List<VersionSpecificationAttribute> _versionSpecificationAttributes;
    private final long _position;

    /**
     * Implementation of builder pattern for <code>DefaultVersionSpecification</code>.
     *
     * @author Matthew Hayter (mhayter at groupon dot com)
     */
    public static final class Builder extends OvalBuilder<DefaultVersionSpecification> {

        /**
         * Public constructor.
         */
        public Builder() {
            super(DefaultVersionSpecification::new);
        }

        /**
         * The uuid.
         *
         * @param value The uuid.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setUuid(final UUID value) {
            _uuid = value;
            return this;
        }

        /**
         * The version set.
         *
         * @param value The VersionSet.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setVersionSet(final VersionSet value) {
            _versionSet = value;
            return this;
        }

        /**
         * The version specification attributes.
         *
         * @param value The versionSpecificationAttributes.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setVersionSpecificationAttributes(final List<VersionSpecificationAttribute> value) {
            _versionSpecificationAttributes = value;
            return this;
        }

        /**
         * The position of the VersionSpecification in the global ordering of VersionSpecifications.
         *
         * @param value The position.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setPosition(final Long value) {
            _position = value;
            return this;
        }

        @NotNull
        private List<VersionSpecificationAttribute> _versionSpecificationAttributes;

        @NotNull
        private VersionSet _versionSet;

        @NotNull
        private UUID _uuid;

        @NotNull
        private Long _position;

    }
}
