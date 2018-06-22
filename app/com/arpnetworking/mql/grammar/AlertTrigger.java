/**
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
package com.arpnetworking.mql.grammar;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.collect.ImmutableMap;
import net.sf.oval.constraint.NotNull;
import org.joda.time.DateTime;

/**
 * Model class to represent when a series was in alert.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
@Loggable
public final class AlertTrigger {
    public DateTime getTime() {
        return _time;
    }

    public DateTime getEndTime() {
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

    private AlertTrigger(final Builder builder) {
        _time = builder._time;
        _endTime = builder._endTime;
        _args = builder._args;
        _message = builder._message;
        _groupBy = builder._groupBy;
    }

    private final DateTime _time;
    private final DateTime _endTime;
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
        public Builder setTime(final DateTime value) {
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
         * Sets the end time of the alert. Null implies the alert is ongoing. Optional. Defaults to null.
         *
         * @param value the operator
         * @return this {@link Builder}
         */
        public Builder setEndTime(final DateTime value) {
            _endTime = value;
            return this;
        }

        @NotNull
        private DateTime _time;
        private DateTime _endTime;
        @NotNull
        private ImmutableMap<String, String> _args = ImmutableMap.of();
        @NotNull
        private ImmutableMap<String, String> _groupBy = ImmutableMap.of();
        @NotNull
        private String _message = "";
    }
}
