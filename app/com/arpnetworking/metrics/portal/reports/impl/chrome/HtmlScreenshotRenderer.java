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

package com.arpnetworking.metrics.portal.reports.impl.chrome;

import models.internal.impl.HtmlReportFormat;
import models.internal.impl.WebPageReportSource;

/**
 * Uses a headless Chrome instance to capture a page.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class HtmlScreenshotRenderer extends BaseScreenshotRenderer<HtmlReportFormat> {
    @Override
    protected byte[] getPageContent(final WebPageReportSource source, final HtmlReportFormat format, final Object todo) {
        return new byte[0]; // TODO(spencerpearson)
    }
}
