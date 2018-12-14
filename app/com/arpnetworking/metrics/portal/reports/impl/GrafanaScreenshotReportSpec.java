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
package com.arpnetworking.metrics.portal.reports.impl;

import java.time.Duration;

public class GrafanaScreenshotReportSpec extends ChromeScreenshotReportSpec {
    public GrafanaScreenshotReportSpec(String url, boolean ignoreCertificateErrors, Duration timeout, double pdfWidthInches, double pdfHeightInches) {
        super(
                url,
                ignoreCertificateErrors,
                "console.log('starting jsRunOnLoad');\n" +
                        "window.addEventListener('reportrendered', () => {\n" +
                        "  console.log('in jsRunOnLoad callback'); var body = document.getElementsByClassName('rendered-markdown-container')[0].srcdoc;\n" +
                        "  document.open(); document.write(body); document.close();\n" +
                        "  setTimeout(() => window.dispatchEvent(new Event('pagereplacedwithreport')), 100);\n" +
                        "});\n" +
                        "console.log('finishing jsRunOnLoad');",
                "pagereplacedwithreport",
                timeout,
                pdfWidthInches,
                pdfHeightInches);
    }
}
