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

package models.internal.reports;

import com.google.common.net.MediaType;
import models.internal.impl.HtmlReportFormat;
import models.internal.impl.PdfReportFormat;

/**
 * A format for a report.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public interface ReportFormat {

    /**
     * The MIME type that documents of this format have.
     *
     * @return The MIME type.
     */
    MediaType getMimeType();

    /**
     * Applies a {@code Visitor} to this format. This should delegate the to the appropriate {@code Visitor#visit} overload.
     *
     * @param formatVisitor the visitor
     * @param <T> the return type of the visitor. Use {@link Void} for visitors that do not need to return a result.
     * @return The result of applying the visitor.
     */
    <T> T accept(Visitor<T> formatVisitor);

    /**
     * {@code Visitor} abstracts over operations which could potentially handle various
     * implementations of ReportFormat.
     *
     * @param <T> the return type of the visitor.
     */
    abstract class Visitor<T> {
        /**
         * Visit an {@code PdfReportFormat}.
         *
         * @param pdfReportFormat The format to visit.
         * @return The result of applying the visitor.
         */
        public abstract T visitPdf(PdfReportFormat pdfReportFormat);

        /**
         * Visit an {@code HtmlReportFormat}.
         *
         * @param htmlReportFormat The format to visit.
         * @return The result of applying the visitor.
         */
        public abstract T visitHtml(HtmlReportFormat htmlReportFormat);
    }
}
