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
import models.internal.VersionSpecificationAttribute;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

/**
 * Default internal model implementation for a version specification attribute.
 *
 * @author Matthew Hayter (mhayter at groupon dot com)
 */
@Loggable
public final class DefaultVersionSpecificationAttribute implements VersionSpecificationAttribute {

    @Override
    public String getKey() {
        return _key;
    }

    @Override
    public String getValue() {
        return _value;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof DefaultVersionSpecificationAttribute)) {
            return false;
        }

        final DefaultVersionSpecificationAttribute otherDefaultVersionSpecificationAttribute = (DefaultVersionSpecificationAttribute) other;
        return Objects.equal(_key, otherDefaultVersionSpecificationAttribute._key)
                && Objects.equal(_value, otherDefaultVersionSpecificationAttribute._value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(_key, _value);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("Key", _key)
                .add("Value", _value)
                .toString();
    }

    private DefaultVersionSpecificationAttribute(final Builder builder) {
        _key = builder._key;
        _value = builder._value;
    }

    private final String _key;
    private final String _value;

    /**
     * Implementation of builder pattern for <code>DefaultVersionSpecificationAttribute</code>.
     *
     * @author Matthew Hayter (mhayter at groupon dot com)
     */
    public static final class Builder extends OvalBuilder<DefaultVersionSpecificationAttribute> {

        /**
         * Public constructor.
         */
        public Builder() {
            super(DefaultVersionSpecificationAttribute::new);
        }

        /**
         * The key.
         *
         * @param value The key.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setKey(final String value) {
            _key = value;
            return this;
        }

        /**
         * The value.
         *
         * @param value The value.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setValue(final String value) {
            _value = value;
            return this;
        }

        @NotNull
        @NotEmpty
        private String _value;

        @NotNull
        @NotEmpty
        private String _key;
    }
}
