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

import com.google.common.base.MoreObjects;

import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Snapshot of a fully rendered report.
 *
 * @author Spencer Pearson
 */
public final class Report {

    /**
     * @param html The HTML version of the report, if that makes sense (else null).
     * @param pdf The PDF version of the report, if that makes sense (else null).
     */
    public Report(@Nullable final String html, @Nullable final byte[] pdf) {
        _html = html;
        _pdf = pdf;
    }

    public @Nullable String getHtml() {
        return _html;
    }

    public @Nullable byte[] getPdf() {
        return _pdf;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("html.length", _html == null ? "<null>" : _html.length())
                .add("pdf.length", _pdf == null ? "<null>" : _pdf.length)
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
                && Arrays.equals(_pdf, report._pdf);
    }

    @Override
    public int hashCode() {

        int result = Objects.hash(_html);
        result = 31 * result + Arrays.hashCode(_pdf);
        return result;
    }

    private @Nullable final String _html;
    private @Nullable final byte[] _pdf;
}
