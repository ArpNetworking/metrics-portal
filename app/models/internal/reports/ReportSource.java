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

import models.internal.impl.ChromeScreenshotReportSource;

import java.util.UUID;

/**
 * A {@code ReportSource} defines a strategy for generating a report.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public interface ReportSource {
    /**
     * Get the id of this report source.
     *
     * @return the id of this report source.
     */
    UUID getId();

    /**
     * Applies a {@code Visitor} to this source. This should delegate the to the appropriate {@code Visitor#visit} overload.
     *
     * @param sourceVisitor the visitor
     * @param <T> the return type of the visitor. Use {@link Void} for visitors that do not need to return a result.
     * @return The result of applying the visitor.
     */
    <T> T accept(Visitor<T> sourceVisitor);

    /**
     * {@code Visitor} abstracts over operations which could potentially handle various
     * implementations of ReportFormat.
     *
     * @param <T> the return type of the visitor.
     */
    interface Visitor<T> {
        /**
         * Visit a {@link ChromeScreenshotReportSource}.
         *
         * @param source The source to visit.
         * @return The result of applying the visitor.
         */
        T visit(ChromeScreenshotReportSource source);

        /**
         * Convenience method equivalent to {@code format.accept(this) }.
         *
         * @param format The format to visit.
         * @return The result of applying the visitor
         */
        default T visit(ReportSource format) {
            return format.accept(this);
        }
    }
}
//
//class ____ {
//    interface RenderedReport { byte[] toBytes(); }
//    interface Format {}
//    interface Source {}
//    interface Renderer<S extends Source, F extends Format> {
//        CompletionStage<RenderedReport> render(S source, F format);
//    }
//    class Report {
//        final Source _source;
//        final Format _format;
//        CompletionStage<Void> execute(final Injector injector) {
//            final Renderer renderer = injector.getInstance(Key.get(Renderer.class,
//            Names.named(_source.getSourceTypeName() + " " + _format.getFormatTypeName())));
//            return renderer.render(_source, _format);
//        }
//    }
//}
