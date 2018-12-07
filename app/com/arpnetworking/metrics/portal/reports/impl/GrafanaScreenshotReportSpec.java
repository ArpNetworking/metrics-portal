/*
 * Copyright 2018 Dropbox
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

import com.github.kklisura.cdt.services.ChromeDevToolsService;

import java.time.Duration;

public class GrafanaScreenshotReportSpec extends ChromeScreenshotReportSpec {
    public GrafanaScreenshotReportSpec(String url, String title, boolean ignoreCertificateErrors, Duration timeout, double pdfWidthInches, double pdfHeightInches) {
        super(url, title, ignoreCertificateErrors, "reportrendered", timeout, pdfWidthInches, pdfHeightInches);
    }
    @Override
    boolean prepare(ChromeDevToolsService dts) {
        return dts.getRuntime().evaluate(JS_CHECK_READY).getResult().getValue().equals(true);
    }

    private static final String JS_CHECK_READY = "(() => {\n" +
        "    var e = document.getElementsByClassName('rendered-markdown-container')[0];\n" +
        "    if (!e) return false;\n" +
        "    var s = e.srcdoc;\n" +
        "    document.open(); document.write(s); document.close();\n" +
        "    return true;\n" +
        "})()";

}
