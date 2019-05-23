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

package models.internal.impl;

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.metrics.portal.reports.RenderedReport;
import com.google.common.base.MoreObjects;
import models.internal.reports.ReportFormat;
import net.sf.oval.constraint.NotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * A document containing the results of a particular rendering of a particular {@link models.internal.reports.Report}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class DefaultRenderedReport implements RenderedReport {
    @Override
    public InputStream getBytes() {
        return new ByteArrayInputStream(_bytes);
    }

    @Override
    public Instant getScheduledFor() {
        return _scheduledFor;
    }

    @Override
    public Instant getGeneratedAt() {
        return _generatedAt;
    }

    @Override
    public ReportFormat getFormat() {
        return _format;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultRenderedReport)) {
            return false;
        }
        final DefaultRenderedReport that = (DefaultRenderedReport) o;
        return Arrays.equals(_bytes, that._bytes)
                && _scheduledFor.equals(that._scheduledFor)
                && _generatedAt.equals(that._generatedAt)
                && _format.equals(that._format);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(_scheduledFor, _generatedAt, _format);
        result = 31 * result + Arrays.hashCode(_bytes);
        return result;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("_bytes", _bytes)
                .add("_scheduledFor", _scheduledFor)
                .add("_generatedAt", _generatedAt)
                .add("_format", _format)
                .toString();
    }

    private final byte[] _bytes;
    private final Instant _scheduledFor;
    private final Instant _generatedAt;
    private final ReportFormat _format;

    /**
     * Builder implementation that constructs {@code DefaultReport}.
     */
    public static final class Builder extends OvalBuilder<DefaultRenderedReport> {
        /**
         * Public Constructor.
         */
        public Builder() {
            super(DefaultRenderedReport::new);
        }

        /**
         * Set the report bytes. Required. Cannot be null.
         *
         * @param bytes The report bytes.
         * @return This instance of {@code Builder}.
         */
        public Builder setBytes(final byte[] bytes) {
            _bytes = bytes.clone();
            return this;
        }

        /**
         * Set the report bytes. Required. Cannot be null.
         *
         * @param scheduledFor The report scheduledFor.
         * @return This instance of {@code Builder}.
         */
        public Builder setScheduledFor(final Instant scheduledFor) {
            _scheduledFor = scheduledFor;
            return this;
        }

        /**
         * Set the report generatedAt. Required. Cannot be null.
         *
         * @param generatedAt The report generatedAt.
         * @return This instance of {@code Builder}.
         */
        public Builder setGeneratedAt(final Instant generatedAt) {
            _generatedAt = generatedAt;
            return this;
        }

        /**
         * Set the report format. Required. Cannot be null.
         *
         * @param format The report format.
         * @return This instance of {@code Builder}.
         */
        public Builder setFormat(final ReportFormat format) {
            _format = format;
            return this;
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

    private DefaultRenderedReport(final Builder builder) {
        _bytes = builder._bytes;
        _scheduledFor = builder._scheduledFor;
        _generatedAt = builder._generatedAt;
        _format = builder._format;
    }
}
