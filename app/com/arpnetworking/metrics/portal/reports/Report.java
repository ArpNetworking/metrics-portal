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

import akka.protobuf.ByteString;
import com.arpnetworking.commons.builder.OvalBuilder;
import com.google.common.base.MoreObjects;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Snapshot of a fully rendered report.
 *
 * @author Spencer Pearson
 */
public final class Report {

    private Report(final Builder builder) {
        _html = builder._html;
        _pdf = builder._pdf;
    }

    public @Nullable String getHtml() {
        return _html;
    }

    public @Nullable ByteString getPdf() {
        return _pdf;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("html.length", _html == null ? "<null>" : _html.length())
                .add("pdf.length", _pdf == null ? "<null>" : _pdf.size())
                .toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Report report = (Report) o;
        return Objects.equals(_html, report._html)
               && Objects.equals(_pdf, report._pdf);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_html, _pdf);
    }

    private @Nullable final String _html;
    private @Nullable final ByteString _pdf;


    /**
     * Builder implementation for {@link Report}.
     */
    public static final class Builder extends OvalBuilder<Report> {

        /**
         * Public constructor.
         */
        public Builder() {
            super(Report::new);
        }

        /**
         * @param value The HTML version of the report, if that makes sense (else null).
         * @return This instance of <code>Builder</code>.
         */
        public Builder setHtml(final String value) {
            _html = value;
            return this;
        }

        /**
         * @param value The PDF version of the report, if that makes sense (else null).
         * @return This instance of <code>Builder</code>.
         */
        public Builder setPdf(final ByteString value) {
            _pdf = value;
            return this;
        }

        /**
         * @param value The PDF version of the report, if that makes sense (else null).
         * @return This instance of <code>Builder</code>.
         */
        public Builder setPdf(final byte[] value) {
            _pdf = ByteString.copyFrom(value);
            return this;
        }

        private String _html;
        private ByteString _pdf;
    }

}
