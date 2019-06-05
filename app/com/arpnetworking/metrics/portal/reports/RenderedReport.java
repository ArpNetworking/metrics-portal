/*
 * Copyright 2019 Dropbox, Inc.
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
import models.internal.reports.ReportFormat;
import net.sf.oval.constraint.NotNull;

import java.io.InputStream;
import java.time.Instant;
import java.util.function.Function;

/**
 * A document containing the results of a particular rendering of a particular {@link models.internal.reports.Report}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public interface RenderedReport {

    /**
     * The format of the report.
     *
     * @return The format.
     */
    ReportFormat getFormat();

    /**
     * The raw bytes of the document.
     *
     * @return The bytes.
     */
    InputStream getBytes();

    /**
     * The instant that the report was scheduled for.
     *
     * @return The instant.
     */
    Instant getScheduledFor();

    /**
     * The instant that the report was actually generated. (Ideally, this should not affect the report content.)
     *
     * @return The instant.
     */
    Instant getGeneratedAt();


    /**
     *
     * Builder implementation that constructs {@code DefaultReport}.
     *
     * @param <B>
     * @param <S>
     */
    abstract class Builder<B extends Builder<B, S>, S extends RenderedReport> extends OvalBuilder<S> {

        protected Builder(final Function<B, S> targetConstructor) {
            super(targetConstructor);
        }

        /**
         * Called by setters to always return appropriate subclass of
         * {@link Builder}, even from setters of base class.
         *
         * @return instance with correct {@link Builder} class type.
         */
        protected abstract B self();

        /**
         * Set the report bytes. Required. Cannot be null.
         *
         * @param bytes The report bytes.
         * @return This instance of {@code Builder}.
         */
        public B setBytes(final byte[] bytes) {
            _bytes = bytes.clone();
            return self();
        }

        /**
         * Set the report bytes. Required. Cannot be null.
         *
         * @param scheduledFor The report scheduledFor.
         * @return This instance of {@code Builder}.
         */
        public B setScheduledFor(final Instant scheduledFor) {
            _scheduledFor = scheduledFor;
            return self();
        }

        /**
         * Set the report generatedAt. Required. Cannot be null.
         *
         * @param generatedAt The report generatedAt.
         * @return This instance of {@code Builder}.
         */
        public B setGeneratedAt(final Instant generatedAt) {
            _generatedAt = generatedAt;
            return self();
        }

        /**
         * Set the report format. Required. Cannot be null.
         *
         * @param format The report format.
         * @return This instance of {@code Builder}.
         */
        public B setFormat(final ReportFormat format) {
            _format = format;
            return self();
        }

        public byte[] getBytes() {
            return _bytes;
        }

        public Instant getScheduledFor() {
            return _scheduledFor;
        }

        public Instant getGeneratedAt() {
            return _generatedAt;
        }

        public ReportFormat getFormat() {
            return _format;
        }

        @NotNull
        private byte[] _bytes;
        @NotNull
        private Instant _scheduledFor;
        @NotNull
        private Instant _generatedAt;
        @NotNull
        private ReportFormat _format;
    }
}
