/*
 * Copyright 2019 Dropbox Inc.
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
package com.arpnetworking.kairos.client.models;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Enumeration representing the possible values for a Kairos relative time unit.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public enum TimeUnit {
    /**
     * Time represents milliseconds.
     */
    MILLISECONDS,
    /**
     * Time represents seconds.
     */
    SECONDS,
    /**
     * Time represents minutes.
     */
    MINUTES,
    /**
     * Time represents hours.
     */
    HOURS,
    /**
     * Time represents days.
     */
    DAYS,
    /**
     * Time represents weeks.
     */
    WEEKS,
    /**
     * Time represents months.
     */
    MONTHS,
    /**
     * Time represents years.
     */
    YEARS;

    /**
     * Encode the enumeration value as lower case.
     *
     * NOTE: KairosDb accepts either upper or lower case representation. This
     * model is otherwise a pass-through but converts all enumeration values
     * to lower case.
     *
     * @return json encoded value
     */
    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.getDefault());
    }
}
