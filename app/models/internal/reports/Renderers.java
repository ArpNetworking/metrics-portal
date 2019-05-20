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

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import models.internal.impl.ChromeScreenshotReportSource;
import models.internal.impl.HtmlReportFormat;
import models.internal.impl.PdfReportFormat;

import java.util.concurrent.CompletionStage;

/**
 * Utilities for rendering reports.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class Renderers {
    /**
     * TODO(spencerpearson).
     * @param injector The injector to retrieve the appropriate renderer from.
     * @param source The {@link ReportSource} to be rendered.
     * @param format The {@link ReportFormat} to be rendered.
     * @param <S> The type of {@link ReportSource} to be rendered.
     * @param <F> The type of {@link ReportFormat} to be rendered.
     * @return TODO(spencerpearson).
     */
    public static <S extends ReportSource, F extends ReportFormat> CompletionStage<RenderedReport> render(
            final Injector injector,
            final S source,
            final F format
    ) {
        @SuppressWarnings("unchecked")
        final Renderer<S, F> renderer = injector.getInstance(Key.get(Renderer.class, Names.named(getKeyName(source, format))));
        return renderer.render(source, format);
    }

    private Renderers() {}

    /**
     * TODO(spencerpearson).
     * @param source TODO(spencerpearson).
     * @param format TODO(spencerpearson).
     * @return TODO(spencerpearson).
     */
    private static String getKeyName(final ReportSource source, final ReportFormat format) {
        return SOURCE_TYPE_VISITOR.visit(source) + " " + FORMAT_TYPE_VISITOR.visit(format);
    }

    private static final ReportSource.Visitor<String> SOURCE_TYPE_VISITOR = new ReportSource.Visitor<String>() {
        @Override
        public String visit(final ChromeScreenshotReportSource source) {
            return "web";
        }
    };
    private static final ReportFormat.Visitor<String> FORMAT_TYPE_VISITOR = new ReportFormat.Visitor<String>() {
        @Override
        public String visit(final HtmlReportFormat format) {
            return "html";
        }
        @Override
        public String visit(final PdfReportFormat format) {
            return "pdf";
        }
    };
}
