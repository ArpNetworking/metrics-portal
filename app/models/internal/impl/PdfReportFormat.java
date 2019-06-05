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
import com.arpnetworking.logback.annotations.Loggable;
import com.google.common.base.MoreObjects;
import com.google.common.net.MediaType;
import models.internal.reports.ReportFormat;
import net.sf.oval.constraint.Min;
import net.sf.oval.constraint.NotNull;

import java.util.Objects;

/**
 * A PDF report format.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@Loggable
public final class PdfReportFormat implements ReportFormat {
    private PdfReportFormat(final Builder builder) {
        _widthInches = builder._widthInches;
        _heightInches = builder._heightInches;
    }

    @Override
    public <T> T accept(final Visitor<T> formatVisitor) {
        return formatVisitor.visitPdf(this);
    }

    @Override
    public String getMimeType() {
        return MediaType.PDF.toString();
    }

    /**
     * Get the width of the PDF in inches.
     *
     * @return the width of the PDF.
     */
    public float getWidthInches() {
        return _widthInches;
    }

    /**
     * Get the height of the PDF in inches.
     *
     * @return the height of the PDF.
     */
    public float getHeightInches() {
        return _heightInches;
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
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PdfReportFormat that = (PdfReportFormat) o;
        return Float.compare(that._widthInches, _widthInches) == 0
                && Float.compare(that._heightInches, _heightInches) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_widthInches, _heightInches);
    }

    private final float _widthInches;
    private final float _heightInches;

    /**
     * Builder implementation that constructs {@code PdfReportFormat}.
     */
    public static final class Builder extends OvalBuilder<PdfReportFormat> {
        /**
         * Public constructor.
         */
        public Builder() {
            super(PdfReportFormat::new);
        }

        /**
         * Set the width. Required. Must be a positive value.
         *
         * @param widthInches The width, in inches.
         * @return This instance of {@code Builder}.
         */
        public Builder setWidthInches(final Float widthInches) {
            _widthInches = widthInches;
            return this;
        }

        /**
         * Set the height. Required. Must be a positive value.
         *
         * @param heightInches The height, in inches.
         * @return This instance of {@code Builder}.
         */
        public Builder setHeightInches(final Float heightInches) {
            _heightInches = heightInches;
            return this;
        }

        @NotNull
        @Min(value = 0, inclusive = false)
        private Float _widthInches;
        @NotNull
        @Min(value = 0, inclusive = false)
        private Float _heightInches;
    }

}
