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

package models.view.reports;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import models.internal.reports.ReportFormat.Visitor;
import models.view.impl.HtmlReportFormat;
import models.view.impl.PdfReportFormat;

/**
 * View model of {@link models.internal.reports.ReportFormat}. Play view models are mutable.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = HtmlReportFormat.class, name = "Html"),
        @JsonSubTypes.Type(value = PdfReportFormat.class, name = "Pdf"),
})
public interface ReportFormat {
    /**
     * Convert to an internal model.
     *
     * @return The internal model.
     */
    models.internal.reports.ReportFormat toInternal();

    /**
     * Convert from an internal model.
     *
     * @param format The internal model.
     * @return the view model.
     */
    static ReportFormat fromInternal(models.internal.reports.ReportFormat format) {
        final Visitor<ReportFormat> fromInternalVisitor = new Visitor<ReportFormat>() {
            @Override
            public ReportFormat visit(final models.internal.impl.PdfReportFormat pdfReportFormat) {
                return PdfReportFormat.fromInternal(pdfReportFormat);
            }

            @Override
            public ReportFormat visit(final models.internal.impl.HtmlReportFormat htmlReportFormat) {
                return HtmlReportFormat.fromInternal(htmlReportFormat);
            }
        };
        return fromInternalVisitor.visit(format);
    }

}
