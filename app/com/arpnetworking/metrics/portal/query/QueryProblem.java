/*
 * Copyright 2018 Inscope Metrics, Inc
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
package com.arpnetworking.metrics.portal.query;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.io.Serializable;

/**
 * Represents a problem with a query. Severity and recovery are left up to consumers.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot com)
 */
public final class QueryProblem implements Serializable {
    public String getProblemCode() {
        return _problemCode;
    }

    public ImmutableList<Object> getArgs() {
        return _args;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("_problemCode", _problemCode)
                .add("_args", _args)
                .toString();
    }

    private QueryProblem(final Builder builder) {
        _problemCode = builder._problemCode;
        _args = builder._args;
    }

    private final String _problemCode;
    private final ImmutableList<Object> _args;

    private static final long serialVersionUID = 1L;

    /**
     * Implementation of the builder pattern for {@link QueryProblem}.
     *
     * @author Brandon Arp (brandon dot arp at smartsheet dot com)
     */
    public static final class Builder extends OvalBuilder<QueryProblem> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(QueryProblem::new);
        }

        /**
         * Sets the problem code of the problem. Required. Cannot be null or empty.
         *
         * @param value the problem code
         * @return this Builder
         */
        public Builder setProblemCode(final String value) {
            _problemCode = value;
            return this;
        }

        /**
         * Sets the arguments used to provide detail in i18n context. Optional. Cannot be null. Defaults to empty map.
         *
         * @param value the i18n replacement arguments
         * @return this Builder
         */
        public Builder setArgs(final ImmutableList<Object> value) {
            _args = value;
            return this;
        }

        @NotNull
        @NotEmpty
        private String _problemCode;

        @NotNull
        private ImmutableList<Object> _args = ImmutableList.of();
    }
}
