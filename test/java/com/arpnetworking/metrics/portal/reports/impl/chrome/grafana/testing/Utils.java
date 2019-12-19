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

package com.arpnetworking.metrics.portal.reports.impl.chrome.grafana.testing;

import java.time.Duration;

/**
 * Utilities for testing Grafana-specific renderers.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class Utils {
    /**
     * HTML for a page that acts like a Grafana page with a report panel on it.
     *
     * @param renderDelay how long to wait between page-load and when the report "finishes rendering"
     * @return the HTML.
     */
    public static String mockGrafanaReportPanelPage(final Duration renderDelay, final boolean succeed) {
        return ""
                + "<html>\n"
                + "<head><script>\n"
                + "  function mockRenderCompletion() {\n"
                + "    document.body.innerHTML = `\n"
                + "      <iframe class=\"rendered-markdown-container\" srcdoc=\"content we care about\"></iframe>\n"
                + "    `;\n"
                + "    window.dispatchEvent(new Event('" + (succeed ? "reportrendered" : "reportrenderfailed") + "'));\n"
                + "  }\n"
                + "  window.addEventListener('load', () => setTimeout(mockRenderCompletion, " + renderDelay.toMillis() + "));\n"
                + "</script></head>\n"
                + "<body>content we do not care about</body>\n"
                + "</html>\n";
    }

    private Utils() {}
}
