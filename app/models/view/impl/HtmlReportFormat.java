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

/**
 * An HTML report format.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
@Loggable
public final class HtmlReportFormat implements ReportFormat {
    private static final HtmlReportFormat INSTANCE = new HtmlReportFormat();

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .toString();
    }

    @Override
    public models.internal.impl.HtmlReportFormat toInternal() {
        return new models.internal.impl.HtmlReportFormat.Builder()
                .build();
    }

    /**
     * Create a {@code HtmlReportFormat} from its internal representation.
     *
     * @param format The internal model.
     * @return The view model.
     */
    public static HtmlReportFormat fromInternal(final models.internal.impl.HtmlReportFormat format) {
        return new HtmlReportFormat();
    }
}
