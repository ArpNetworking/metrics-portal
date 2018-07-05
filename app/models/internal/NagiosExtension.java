/*
 * Copyright 2016 Groupon.com
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
package models.internal;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.base.MoreObjects;
import net.sf.oval.constraint.Min;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;
import org.joda.time.Duration;

import java.util.Objects;

/**
 * Represents the nagios specific data for an alert.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
@Loggable
public final class NagiosExtension {

    public String getSeverity() {
        return _severity;
    }

    public String getNotify() {
        return _notify;
    }

    public int getMaxCheckAttempts() {
        return _maxCheckAttempts;
    }

    public Duration getFreshnessThreshold() {
        return _freshnessThreshold;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", Integer.toHexString(System.identityHashCode(this)))
                .add("class", this.getClass())
                .add("Severity", _severity)
                .add("Notify", _notify)
                .add("MaxCheckAttempts", _maxCheckAttempts)
                .add("FreshnessThreshold", _freshnessThreshold)
                .toString();
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof NagiosExtension)) {
            return false;
        }

        final NagiosExtension otherExtension = (NagiosExtension) other;
        return Objects.equals(_severity, otherExtension._severity)
                && Objects.equals(_notify, otherExtension._notify)
                && _maxCheckAttempts == otherExtension._maxCheckAttempts
                && Objects.equals(_freshnessThreshold, otherExtension._freshnessThreshold);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                _severity,
                _notify,
                _maxCheckAttempts,
                _freshnessThreshold);
    }

    private NagiosExtension(final Builder builder) {
        _severity = builder._severity;
        _notify = builder._notify;
        _maxCheckAttempts = builder._maxCheckAttempts;
        _freshnessThreshold = Duration.standardSeconds(builder._freshnessThresholdInSeconds);
    }

    private final String _severity;
    private final String _notify;
    private final int _maxCheckAttempts;
    private final Duration _freshnessThreshold;

    /**
     * Builder implementation for <code>NagiosExtension</code>.
     */
    public static final class Builder extends OvalBuilder<NagiosExtension> {

        /**
         * Public constructor.
         */
        public Builder() {
            super(NagiosExtension::new);
        }

        /**
         * The severity. Required. Cannot be null.
         *
         * @param value The severity.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setSeverity(final String value) {
            _severity = value;
            return this;
        }

        /**
         * The email to notify to. Required. Cannot be null.
         *
         * @param value The notification address.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setNotify(final String value) {
            _notify = value;
            return this;
        }

        /**
         * The maximum number of attempts to check the value. Required. Cannot be null.
         *
         * @param value The maximum number of check attempts.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setMaxCheckAttempts(final Integer value) {
            _maxCheckAttempts = value;
            return this;
        }

        /**
         * The freshness threshold in seconds. Required. Cannot be null.
         *
         * @param value The freshness threshold in seconds.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setFreshnessThresholdInSeconds(final Long value) {
            _freshnessThresholdInSeconds = value;
            return this;
        }

        @NotNull
        @NotEmpty
        private String _severity;
        @NotNull
        @NotEmpty
        private String _notify;
        @NotNull
        @Min(1)
        private Integer _maxCheckAttempts;
        @NotNull
        @Min(0)
        private Long _freshnessThresholdInSeconds;
    }

}
