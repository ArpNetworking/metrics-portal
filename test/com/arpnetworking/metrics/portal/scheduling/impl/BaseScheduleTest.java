package com.arpnetworking.metrics.portal.scheduling.impl;

import org.junit.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public final class BaseScheduleTest {

    private static final ZonedDateTime t0 = ZonedDateTime.parse("2018-01-01T00:00:00Z");

    private static final class MinimalSchedule extends BaseSchedule {

        public static final MinimalSchedule INSTANCE = new Builder().build();

        private MinimalSchedule(final Builder builder) {
            super(builder);
        }

        @Override
        protected Optional<ZonedDateTime> unboundedNextRun(Optional<ZonedDateTime> lastRun) {
            return Optional.empty();
        }

        /**
         * Implementation of builder pattern for {@link MinimalSchedule}.
         *
         * @author Spencer Pearson (spencerpearson at dropbox dot com)
         */
        private static final class Builder extends BaseSchedule.Builder<Builder, MinimalSchedule> {
            private Builder() {
                super(MinimalSchedule::new);
            }

            @Override
            protected Builder self() {
                return this;
            }
        }
    }

    @Test(expected = net.sf.oval.exception.ConstraintsViolatedException.class)
    public void testBuilderRejectsRunAfterAfterRunUntil() {
        new MinimalSchedule.Builder()
                .setRunAtAndAfter(t0)
                .setRunUntil(t0.minus(Duration.ofSeconds(1)))
                .build();
    }

}
