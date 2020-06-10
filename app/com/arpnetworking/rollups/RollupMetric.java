package com.arpnetworking.rollups;

import com.arpnetworking.commons.builder.ThreadLocalBuilder;
import com.google.common.base.MoreObjects;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.util.Objects;
import java.util.Optional;

public class RollupMetric {
    private final String _baseMetricName;
    private final RollupPeriod _period;

    public String getBaseMetricName() {
        return _baseMetricName;
    }

    public String getRollupMetricName() {
        return _baseMetricName + _period.getSuffix();
    }

    public RollupPeriod getPeriod() {
        return _period;
    }

    public Optional<RollupMetric> nextFiner() {
        return _period.nextSmallest().map(finerPeriod ->
            ThreadLocalBuilder.clone(this, Builder.class, b -> b
                    .setPeriod(finerPeriod)
            )
        );
    }

    public static Optional<RollupMetric> fromRollupMetricName(final String name) {
        for (final RollupPeriod period : RollupPeriod.values()) {
            if (name.endsWith(period.getSuffix())) {
                return Optional.of(ThreadLocalBuilder.build(Builder.class, b -> b
                        .setBaseMetricName(name.substring(0, name.length() - period.getSuffix().length()))
                        .setPeriod(period)
                ));
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RollupMetric that = (RollupMetric) o;
        return _baseMetricName.equals(that._baseMetricName)
                && _period == that._period;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_baseMetricName, _period);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("_baseMetricName", _baseMetricName)
                .add("_period", _period)
                .toString();
    }

    private RollupMetric(final Builder builder) {
        _baseMetricName = builder._baseMetricName;
        _period = builder._period;
    }


    /**
     * {@link RollupMetric} builder static inner class.
     */
    public static final class Builder extends ThreadLocalBuilder<RollupMetric> {

        /**
         * Creates a builder.
         */
        public Builder() {
            super(RollupMetric::new);
        }

        /**
         * Sets the {@code _baseMetricName} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code _baseMetricName} to set
         * @return a reference to this Builder
         */
        public Builder setBaseMetricName(final String value) {
            _baseMetricName = value;
            return this;
        }

        /**
         * Sets the {@code _period} and returns a reference to this Builder so that the methods can be chained together.
         *
         * @param value the {@code _period} to set
         * @return a reference to this Builder
         */
        public Builder setPeriod(final RollupPeriod value) {
            _period = value;
            return this;
        }


        @Override
        protected void reset() {
            _baseMetricName = null;
            _period = null;
        }

        @NotNull
        @NotEmpty
        private String _baseMetricName;
        @NotNull
        private RollupPeriod _period;
    }
}
