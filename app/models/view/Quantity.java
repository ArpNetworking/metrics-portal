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
package models.view;

import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.base.MoreObjects;

import javax.annotation.Nullable;

/**
 * View model of {@link models.internal.Quantity}. Play view models are mutable.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
@Loggable
public final class Quantity {

    public void setValue(final double value) {
        _value = value;
    }

    public double getValue() {
        return _value;
    }

    public void setUnit(@Nullable final String value) {
        _unit = value;
    }

    @Nullable
    public String getUnit() {
        return _unit;
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

    private double _value;
    private String _unit;
}
