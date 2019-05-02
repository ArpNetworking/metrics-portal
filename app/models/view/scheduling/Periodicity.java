/*
 * Copyright 2019 Dropbox, Inc.
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
package models.view.scheduling;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.Maps;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * {@code Periodicity} represents a repetition strategy for a {@link Schedule}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public enum Periodicity {
    /**
     * Every minute.
     */
    MINUTELY("Minutely", ChronoUnit.MINUTES),
    /**
     * Every hour.
     */
    HOURLY("Hourly", ChronoUnit.HOURS),
    /**
     * Every day.
     */
    DAILY("Daily", ChronoUnit.DAYS);

    private static final Map<String, Periodicity> BY_NAME = Maps.newHashMap();

    static {
        for (Periodicity p : Periodicity.values()) {
            BY_NAME.put(p._name, p);
        }
    }

    private final String _name;
    private final ChronoUnit _unit;

    Periodicity(final String name, final ChronoUnit unit) {
        _name = name;
        _unit = unit;
    }

    /**
     * Get an instance of {@link Periodicity} by its name.
     *
     * @param name The name of the value.
     * @return The {@code Periodicity} instance with that name, or {@code null}.
     */
    @JsonCreator
    @Nullable
    public static Periodicity fromName(final String name) {
        return BY_NAME.get(name);
    }

    /**
     * Get an instance of {@link Periodicity} that is equivalent to the given value.
     *
     * @param value An equivalent instance of {@link ChronoUnit}
     * @return An optional of the {@code Periodicity} with this value, otherwise {@link Optional#empty()}.
     */
    public static Optional<Periodicity> fromValue(final ChronoUnit value) {
        return BY_NAME.values()
                .stream()
                .filter(p -> p._unit.equals(value))
                .findFirst();
    }

    @JsonValue
    public String getName() {
        return _name;
    }

    /**
     * Convert this value into its internal representation, a {@link ChronoUnit}.
     *
     * @return the {@code ChronoUnit} equivalent of this value.
     */
    public ChronoUnit toInternal() {
        return _unit;
    }
}
