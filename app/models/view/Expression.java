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
package models.view;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.base.MoreObjects;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.util.UUID;

/**
 * View model of <code>Expression</code>.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
@Loggable
public final class Expression {

    public UUID getId() {
        return _id;
    }

    public String getCluster() {
        return _cluster;
    }

    public String getService() {
        return _service;
    }

    public String getMetric() {
        return _metric;
    }

    public String getScript() {
        return _script;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", Integer.toHexString(System.identityHashCode(this)))
                .add("class", this.getClass())
                .add("Id", _id)
                .add("Cluster", _cluster)
                .add("Service", _service)
                .add("Metric", _metric)
                .add("Script", _script)
                .toString();
    }

    private Expression(final Builder builder) {
        _id = builder._id;
        _cluster = builder._cluster;
        _service = builder._service;
        _metric = builder._metric;
        _script = builder._script;
    }

    private final UUID _id;
    private final String _cluster;
    private final String _service;
    private final String _metric;
    private final String _script;

    /**
     * Implementation of the builder pattern for {@link Expression}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends OvalBuilder<Expression> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(Expression::new);
        }

        /**
         * Sets the id. Required. Cannot be null or empty.
         *
         * @param value the value
         * @return this {@link Builder}
         */
        public Builder setId(final UUID value) {
            _id = value;
            return this;
        }

        /**
         * Sets the cluster. Required. Cannot be null or empty.
         *
         * @param value the value
         * @return this {@link Builder}
         */
        public Builder setCluster(final String value) {
            _cluster = value;
            return this;
        }

        /**
         * Sets the service. Required. Cannot be null or empty.
         *
         * @param value the value
         * @return this {@link Builder}
         */
        public Builder setService(final String value) {
            _service = value;
            return this;
        }

        /**
         * Sets the metric. Required. Cannot be null or empty.
         *
         * @param value the value
         * @return this {@link Builder}
         */
        public Builder setMetric(final String value) {
            _metric = value;
            return this;
        }

        /**
         * Sets the script. Required. Cannot be null or empty.
         *
         * @param value the value
         * @return this {@link Builder}
         */
        public Builder setScript(final String value) {
            _script = value;
            return this;
        }

        @NotNull
        @NotEmpty
        private UUID _id;
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
        private String _script;
    }

}
