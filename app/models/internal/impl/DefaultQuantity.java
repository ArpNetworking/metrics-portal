/*
 * Copyright 2014 Groupon.com
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
import models.internal.Quantity;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.util.Optional;

/**
 * Default internal model implementation for a quantity.
 *
 * TODO(vkoskela): This should probably be unified with the tsd-core type Quantity.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
@Loggable
public final class DefaultQuantity implements Quantity {

    @Override
    public double getValue() {
        return _value;
    }

    @Override
    public Optional<String> getUnit() {
        return _unit;
    }

    @Override
    public boolean equals(final Object other) {
        if (other == this) {
            return true;
        }

        if (!(other instanceof DefaultQuantity)) {
            return false;
        }

        final DefaultQuantity otherQuantity = (DefaultQuantity) other;
        return Double.compare(_value, otherQuantity._value) == 0
                && Objects.equal(_unit, otherQuantity._unit);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(_value, _unit);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", Integer.toHexString(System.identityHashCode(this)))
                .add("class", this.getClass())
                .add("Value", _value)
                .add("Unit", _unit)
                .toString();
    }

    private DefaultQuantity(final Builder builder) {
        _value = builder._value.doubleValue();
        _unit = Optional.ofNullable(builder._unit);
    }

    private final double _value;
    private final Optional<String> _unit;

    /**
     * Implementation of builder pattern for <code>DefaultQuantity</code>.
     *
     * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
     */
    public static final class Builder extends OvalBuilder<Quantity> {

        /**
         * Public constructor.
         */
        public Builder() {
            super(DefaultQuantity::new);
        }

        /**
         * The value. Cannot be null.
         *
         * @param value The value.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setValue(final Double value) {
            _value = value;
            return this;
        }

        /**
         * The unit of the value. Optional. Cannot be empty. Default is null.
         *
         * @param value The unit.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setUnit(final String value) {
            _unit = value;
            return this;
        }

        @NotNull
        private Double _value;
        @NotEmpty
        private String _unit;
    }
}
