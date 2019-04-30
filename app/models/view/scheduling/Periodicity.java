package models.view.scheduling;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.Maps;

import java.time.temporal.ChronoUnit;
import java.util.Map;

public enum Periodicity {
    /** Every minute. */
    MINUTELY("Minutely", ChronoUnit.MINUTES),
    /** Every hour. */
    HOURLY("Hourly", ChronoUnit.HOURS),
    /** Every day. */
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

    @JsonCreator
    public static Periodicity fromName(final String name) {
        return BY_NAME.get(name);
    }

    public static Periodicity fromValue(final ChronoUnit value) {
        if (value.equals(MINUTELY._unit)) {
            return MINUTELY;
        } else if (value.equals(HOURLY._unit)) {
            return HOURLY;
        } else if (value.equals(DAILY._unit)) {
            return DAILY;
        } else {
            throw new IllegalArgumentException("No Periodicity for value " + value);
        }
    }

    @JsonValue
    public String getName() {
        return _name;
    }

    public ChronoUnit toInternal() {
        return _unit;
    }
}
