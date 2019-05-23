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

package models.view.impl;

import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.base.MoreObjects;
import models.view.reports.ReportFormat;

import java.util.Objects;

/**
 * A PDF report format.
 *
 * Play view models are mutable.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
@Loggable
public final class PdfReportFormat implements ReportFormat {

    public void setWidthInches(final float value) {
        _widthInches = value;
    }

    public float getWidthInches() {
        return _widthInches;
    }

    public void setHeightInches(final float value) {
        _heightInches = value;
    }

    public float getHeightInches() {
        return _heightInches;
    }

    @Override
    public models.internal.reports.ReportFormat toInternal() {
        return new models.internal.impl.PdfReportFormat.Builder()
                .setWidthInches(_widthInches)
                .setHeightInches(_heightInches)
                .build();
    }

    /**
     * Create a {@code PdfReportFormat} from its internal representation.
     *
     * @param format The internal model.
     * @return The view model.
     */
    public static PdfReportFormat fromInternal(final models.internal.impl.PdfReportFormat format) {
        final PdfReportFormat viewFormat = new PdfReportFormat();
        viewFormat.setWidthInches(format.getWidthInches());
        viewFormat.setHeightInches(format.getHeightInches());
        return viewFormat;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("widthInches", _widthInches)
                .add("heightInches", _heightInches)
                .toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PdfReportFormat)) {
            return false;
        }
        final PdfReportFormat otherPdfReportFormat = (PdfReportFormat) o;
        return Float.compare(otherPdfReportFormat._widthInches, _widthInches) == 0
                && Float.compare(otherPdfReportFormat._heightInches, _heightInches) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_widthInches, _heightInches);
    }

    private float _widthInches;
    private float _heightInches;

}
