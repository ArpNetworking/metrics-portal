package com.arpnetworking.kairos.client.models;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.sf.oval.constraint.Min;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;
import org.joda.time.Period;

/**
 * Model class to represent the sampling field of an aggregator.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class Sampling {
    public String getUnit() {
        return _unit;
    }

    public int getValue() {
        return _value;
    }

    private Sampling(final Builder builder) {
        _unit = builder._unit;
        _value = builder._value;
    }

    private final String _unit;
    private final int _value;

    /**
     * Implementation of the builder pattern for a {@link Sampling}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends OvalBuilder<Sampling> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(Sampling::new);
        }

        /**
         * Sets the value and unit from a {@link Period}.
         *
         * @param value a {@link Period}
         * @return this {@link Builder}
         */
        public Builder setPeriod(final Period value) {
            _value = value.toStandardSeconds().getSeconds();
            _unit = "seconds";
            return this;
        }

        @JsonProperty("value")
        @Min(1)
        private int _value = 1;
        @JsonProperty("unit")
        @NotNull
        @NotEmpty
        private String _unit = "minutes";
    }
}
