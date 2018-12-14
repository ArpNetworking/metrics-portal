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

import com.arpnetworking.metrics.portal.reports.Report;
import com.arpnetworking.metrics.portal.reports.ReportSpec;

import java.time.Duration;
import java.util.concurrent.CompletionStage;


public class ChromeScreenshotReportSpec implements ReportSpec {

    public ChromeScreenshotReportSpec(String url, boolean ignoreCertificateErrors, String jsRunOnLoad, String triggeringEventName, Duration timeout, double pdfWidthInches, double pdfHeightInches) {
        this.url = url;
        this.jsRunOnLoad = jsRunOnLoad;
        this.ignoreCertificateErrors = ignoreCertificateErrors;
        this.triggeringEventName = triggeringEventName;
        this.timeout = timeout;
        this.pdfWidthInches = pdfWidthInches;
        this.pdfHeightInches = pdfHeightInches;
    }

    public String getUrl() {
        return url;
    }

    public boolean isIgnoreCertificateErrors() {
        return ignoreCertificateErrors;
    }

    public String getJsRunOnLoad() {
        return jsRunOnLoad;
    }

    public String getTriggeringEventName() {
        return triggeringEventName;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public double getPdfWidthInches() {
        return pdfWidthInches;
    }

    public double getPdfHeightInches() {
        return pdfHeightInches;
    }

    @Override
    public CompletionStage<Report> render() {
        return new ChromeScreenshotTaker().render(this);
    }

    private final String url;
    private final boolean ignoreCertificateErrors;
    private final String jsRunOnLoad;
    private final String triggeringEventName;
    private final Duration timeout;
    private final double pdfWidthInches;
    private final double pdfHeightInches;
}
