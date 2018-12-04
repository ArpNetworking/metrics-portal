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

public class GrafanaReportPanelScreenshotReportGenerator extends ChromeScreenshotReportGenerator {

    public GrafanaReportPanelScreenshotReportGenerator(String url, boolean ignoreCertificateErrors, long timeoutMillis, double pdfWidthInches, double pdfHeightInches) {
        super(
                url,
                dts -> dts.getRuntime().evaluate(jsCheckReady).getResult().getValue().equals(true),
                ignoreCertificateErrors,
                timeoutMillis,
                pdfWidthInches,
                pdfHeightInches
        );
    }

    private static String jsCheckReady = "(() => {\n" +
            "    var e = document.getElementsByClassName('rendered-markdown-container')[0];\n" +
            "    if (!e) return false;\n" +
            "    var s = e.srcdoc;\n" +
            "    document.open(); document.write(s); document.close();\n" +
            "    return true;\n" +
            "})()";

}
