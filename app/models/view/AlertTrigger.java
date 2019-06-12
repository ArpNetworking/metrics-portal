/*
 * Copyright 2017 Smartsheet
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

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.collect.ImmutableMap;
import models.internal.impl.DefaultAlertTrigger;
import net.sf.oval.constraint.CheckWith;
import net.sf.oval.constraint.CheckWithCheck;
import net.sf.oval.constraint.NotNull;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * View model of an alert trigger.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
@Loggable
public final class AlertTrigger {
    /**
     * Creates a view model from an internal model.
     *
     * @param internal the internal model
     * @return a new view model
     */
    public static AlertTrigger fromInternal(final models.internal.AlertTrigger internal) {
        return new AlertTrigger.Builder()
                .setArgs(internal.getArgs())
                .setEndTime(internal.getEndTime())
                .setGroupBy(internal.getGroupBy())
                .setMessage(internal.getMessage())
                .setTime(internal.getTime())
                .build();
    }

    public ZonedDateTime getTime() {
        return _time;
    }

    public Optional<ZonedDateTime> getEndTime() {
        return _endTime;
    }

    public ImmutableMap<String, String> getArgs() {
        return _args;
    }

    public ImmutableMap<String, String> getGroupBy() {
        return _groupBy;
    }

    public String getMessage() {
        return _message;
    }

    /**
     * Creates an internal model from this view model.
     *
     * @return a new internal model
     */
    public models.internal.AlertTrigger toInternal() {
        return new DefaultAlertTrigger.Builder()
                .setArgs(_args)
                .setEndTime(_endTime)
                .setGroupBy(_groupBy)
                .setMessage(_message)
                .setTime(_time)
                .build();
    }
    private AlertTrigger(final Builder builder) {
        _time = builder._time;
        _endTime = builder._endTime;
        _args = builder._args;
        _message = builder._message;
        _groupBy = builder._groupBy;
    }

    private final ZonedDateTime _time;
    private final Optional<ZonedDateTime> _endTime;
    private final String _message;
    private final ImmutableMap<String, String> _groupBy;
    private final ImmutableMap<String, String> _args;

    /**
     * Implementation of the Builder pattern for {@link AlertTrigger}.
     */
    public static class Builder extends OvalBuilder<AlertTrigger> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(AlertTrigger::new);
        }

        /**
         * Sets the start time of the alert. Required. Cannot be null.
         *
         * @param value the operator
         * @return this {@link Builder}
         */
        public Builder setTime(final ZonedDateTime value) {
            _time = value;
            return this;
        }

        /**
         * Sets a message for why this series was in alert. Optional. Cannot be null. Defaults to empty string.
         *
         * @param value the message
         * @return this {@link Builder}
         */
        public Builder setMessage(final String value) {
            _message = value;
            return this;
        }

        /**
         * Sets the args of the alert. Optional. Cannot be null.
         *
         * @param value the args
         * @return this {@link Builder}
         */
        public Builder setArgs(final ImmutableMap<String, String> value) {
            _args = value;
            return this;
        }

        /**
         * Sets the "group by" portion of the arguments that make this distinct from other triggers. Optional. Cannot be null.
         *
         * @param value the group by parameters
         * @return this {@link Builder}
         */
        public Builder setGroupBy(final ImmutableMap<String, String> value) {
            _groupBy = value;
            return this;
        }

        /**
         * Sets the end time of the alert. Empty implies the alert is ongoing. Optional. Defaults to empty.
         *
         * @param value the operator
         * @return this {@link Builder}
         */
        public Builder setEndTime(final Optional<ZonedDateTime> value) {
            _endTime = value;
            return this;
        }

        @NotNull
        private ZonedDateTime _time;
        @NotNull
        @CheckWith(EndAfterStart.class)
        private Optional<ZonedDateTime> _endTime = Optional.empty();
        @NotNull
        private ImmutableMap<String, String> _args = ImmutableMap.of();
        @NotNull
        private ImmutableMap<String, String> _groupBy = ImmutableMap.of();
        @NotNull
        private String _message = "";

        private static final class EndAfterStart implements CheckWithCheck.SimpleCheck {

            @Override
            public boolean isSatisfied(final Object obj, final Object val) {
                if (obj instanceof Builder) {
                    final Builder builder = (Builder) obj;
                    return builder._endTime.map(endTime -> !endTime.isBefore(builder._time)).orElse(true);
                }

                return false;
            }

            private static final long serialVersionUID = 1;
        }
    }
}
