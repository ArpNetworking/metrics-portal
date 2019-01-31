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

/**
 * Enumeration representing the possible values for a Kairos sampling unit.
 *
 * @author Gilligan Markham (gmarkham at dropbox dot com)
 */
public enum SamplingUnit {
    /**
     * Sampling is count represents milliseconds.
     */
    MILLISECONDS("milliseconds"),
    /**
     * Sampling is count represents seconds.
     */
    SECONDS("seconds"),
    /**
     * Sampling is count represents minutes.
     */
    MINUTES("minutes"),
    /**
     * Sampling is count represents hours.
     */
    HOURS("hours"),
    /**
     * Sampling is count represents days.
     */
    DAYS("days"),
    /**
     * Sampling is count represents weeks.
     */
    WEEKS("weeks"),
    /**
     * Sampling is count represents months.
     */
    MONTHS("months"),
    /**
     * Sampling is count represents years.
     */
    YEARS("years");

    SamplingUnit(final String value) {
        _value = value;
    }


    @JsonValue
    public String getValue() {
        return _value;
    }


    private final String _value;
}
