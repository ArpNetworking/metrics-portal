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

import com.arpnetworking.metrics.portal.reports.Report;
import com.arpnetworking.metrics.portal.reports.ReportSpec;

import java.time.Duration;
import java.util.concurrent.CompletionStage;


public class ChromeScreenshotReportSpec implements ReportSpec {

    public ChromeScreenshotReportSpec(String url, String title, boolean ignoreCertificateErrors, String triggeringEventName, Duration timeout, double pdfWidthInches, double pdfHeightInches) {
        this.url = url;
        this.title = title;
        this.ignoreCertificateErrors = ignoreCertificateErrors;
        this.triggeringEventName = triggeringEventName;
        this.timeout = timeout;
        this.pdfWidthInches = pdfWidthInches;
        this.pdfHeightInches = pdfHeightInches;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isIgnoreCertificateErrors() {
        return ignoreCertificateErrors;
    }

    public void setIgnoreCertificateErrors(boolean ignoreCertificateErrors) {
        this.ignoreCertificateErrors = ignoreCertificateErrors;
    }

    public String getTriggeringEventName() {
        return triggeringEventName;
    }

    public void setTriggeringEventName(String triggeringEventName) {
        this.triggeringEventName = triggeringEventName;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public double getPdfWidthInches() {
        return pdfWidthInches;
    }

    public void setPdfWidthInches(double pdfWidthInches) {
        this.pdfWidthInches = pdfWidthInches;
    }

    public double getPdfHeightInches() {
        return pdfHeightInches;
    }

    public void setPdfHeightInches(double pdfHeightInches) {
        this.pdfHeightInches = pdfHeightInches;
    }

    @Override
    public CompletionStage<Report> render() {
        return new ChromeScreenshotTaker().render(this);
    }

    private String url;
    private String title;
    private boolean ignoreCertificateErrors;
    private String triggeringEventName;
    private Duration timeout;
    private double pdfWidthInches;
    private double pdfHeightInches;
}
