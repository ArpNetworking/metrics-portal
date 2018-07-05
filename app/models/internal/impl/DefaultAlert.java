/*
 * Copyright 2015 Groupon.com
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
package models.internal.impl;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.base.MoreObjects;
import models.internal.Alert;
import models.internal.Context;
import models.internal.NagiosExtension;
import models.internal.Operator;
import models.internal.Quantity;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;
import org.joda.time.Period;

import java.util.Objects;
import java.util.UUID;

/**
 * Default internal model implementation for an alert.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
@Loggable
public final class DefaultAlert implements Alert {

    @Override
    public UUID getId() {
        return _id;
    }

    @Override
    public Context getContext() {
        return _context;
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public String getCluster() {
        return _cluster;
    }

    @Override
    public String getService() {
        return _service;
    }

    @Override
    public String getMetric() {
        return _metric;
    }

    @Override
    public String getStatistic() {
        return _statistic;
    }

    @Override
    public Period getPeriod() {
        return _period;
    }

    @Override
    public Operator getOperator() {
        return _operator;
    }

    @Override
    public Quantity getValue() {
        return _value;
    }

    @Override
    public NagiosExtension getNagiosExtension() {
        return _nagiosExtension;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", Integer.toHexString(System.identityHashCode(this)))
                .add("class", this.getClass())
                .add("Id", _id)
                .add("Context", _context)
                .add("Name", _name)
                .add("Cluster", _cluster)
                .add("Service", _service)
                .add("Metric", _metric)
                .add("Statistic", _statistic)
                .add("Period", _period)
                .add("Operator", _operator)
                .add("Value", _value)
                .add("NagiosExtensions", _nagiosExtension)
                .toString();
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof DefaultAlert)) {
            return false;
        }

        final DefaultAlert otherAlert = (DefaultAlert) other;
        return Objects.equals(_id, otherAlert._id)
                && Objects.equals(_context, otherAlert._context)
                && Objects.equals(_name, otherAlert._name)
                && Objects.equals(_cluster, otherAlert._cluster)
                && Objects.equals(_service, otherAlert._service)
                && Objects.equals(_metric, otherAlert._metric)
                && Objects.equals(_statistic, otherAlert._statistic)
                && Objects.equals(_period.normalizedStandard(), otherAlert._period.normalizedStandard())
                && Objects.equals(_operator, otherAlert._operator)
                && Objects.equals(_value, otherAlert._value)
                && Objects.equals(_nagiosExtension, otherAlert._nagiosExtension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                _id,
                _context,
                _name,
                _cluster,
                _service,
                _metric,
                _statistic,
                _period,
                _operator,
                _value,
                _nagiosExtension);
    }

    private DefaultAlert(final Builder builder) {
        _id = builder._id;
        _context = builder._context;
        _name = builder._name;
        _cluster = builder._cluster;
        _service = builder._service;
        _metric = builder._metric;
        _statistic = builder._statistic;
        _period = builder._period;
        _operator = builder._operator;
        _value = builder._value;
        _nagiosExtension = builder._nagiosExtension;
    }

    private final UUID _id;
    private final Context _context;
    private final String _name;
    private final String _cluster;
    private final String _service;
    private final String _metric;
    private final String _statistic;
    private final Period _period;
    private final Operator _operator;
    private final Quantity _value;
    private final NagiosExtension _nagiosExtension;

    /**
     * Builder implementation for <code>DefaultAlert</code>.
     */
    public static final class Builder extends OvalBuilder<DefaultAlert> {

        /**
         * Public constructor.
         */
        public Builder() {
            super(DefaultAlert::new);
        }

        /**
         * The identifier. Required. Cannot be null.
         *
         * @param value The identifier.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setId(final UUID value) {
            _id = value;
            return this;
        }

        /**
         * The context. Required. Cannot be null or empty.
         *
         * @param value The context.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setContext(final Context value) {
            _context = value;
            return this;
        }

        /**
         * The name. Required. Cannot be null or empty.
         *
         * @param value The name.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setName(final String value) {
            _name = value;
            return this;
        }

        /**
         * The cluster. Required. Cannot be null or empty.
         *
         * @param value The cluster.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setCluster(final String value) {
            _cluster = value;
            return this;
        }

        /**
         * The service. Required. Cannot be null or empty.
         *
         * @param value The service.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setService(final String value) {
            _service = value;
            return this;
        }

        /**
         * The metric. Required. Cannot be null or empty.
         *
         * @param value The metric.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setMetric(final String value) {
            _metric = value;
            return this;
        }

        /**
         * The statistic. Required. Cannot be null or empty.
         *
         * @param value The statistic.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setStatistic(final String value) {
            _statistic = value;
            return this;
        }

        /**
         * The period. Required. Cannot be null or empty.
         *
         * @param value The period.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setPeriod(final Period value) {
            _period = value;
            return this;
        }

        /**
         * The operator. Required. Cannot be null or empty.
         *
         * @param value The operator.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setOperator(final Operator value) {
            _operator = value;
            return this;
        }

        /**
         * The value. Required. Cannot be null or empty.
         *
         * @param value The value.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setValue(final Quantity value) {
            _value = value;
            return this;
        }

        /**
         * The nagios specific extensions.
         *
         * @param value The extensions.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setNagiosExtension(final NagiosExtension value) {
            _nagiosExtension = value;
            return this;
        }

        @NotNull
        private UUID _id;
        @NotNull
        private Context _context;
        @NotNull
        @NotEmpty
        private String _name;
        @NotNull
        @NotEmpty
        private String _cluster;
        @NotNull
        @NotEmpty
        private String _service;
        @NotNull
        @NotEmpty
        private String _metric;
        @NotNull
        @NotEmpty
        private String _statistic;
        @NotNull
        private Period _period;
        @NotNull
        private Operator _operator;
        @NotNull
        private Quantity _value;
        private NagiosExtension _nagiosExtension;
    }
}
