/*
 * Copyright 2018 Dropbox, Inc.
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
package com.arpnetworking.metrics.portal.reports;

import com.arpnetworking.commons.builder.OvalBuilder;
import net.sf.oval.constraint.NotNull;

/**
 * A (possibly recurring) job describing how to generate a {@link Report} and where to send it.
 *
 * @author Spencer Pearson
 */
public final class Job {
    private final ReportSpec _spec;
    private final ReportSink _sink;
    private final Schedule _schedule;

    private Job(final Builder builder) {
        _spec = builder._spec;
        _sink = builder._sink;
        _schedule = builder._schedule;
    }

    public ReportSpec getSpec() {
        return _spec;
    }

    public ReportSink getSink() {
        return _sink;
    }

    public Schedule getSchedule() {
        return _schedule;
    }


    /**
     * Builder implementation for {@link Job}.
     */
    public static final class Builder extends OvalBuilder<Job> {

        /**
         * Public constructor.
         */
        public Builder() {
            super(Job::new);
        }

        /**
         * The report-generation spec. Required. Cannot be null.
         *
         * @param value The severity.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setSpec(final ReportSpec value) {
            _spec = value;
            return this;
        }

        /**
         * The place reports should be sent to. Required. Cannot be null.
         *
         * @param value The notification address.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setSink(final ReportSink value) {
            _sink = value;
            return this;
        }

        /**
         * The schedule on which the report should be generated/sent. Required. Cannot be null.
         *
         * @param value The maximum number of check attempts.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setSchedule(final Schedule value) {
            _schedule = value;
            return this;
        }

        @NotNull
        private ReportSpec _spec;
        @NotNull
        private ReportSink _sink;
        @NotNull
        private Schedule _schedule;
    }


}

