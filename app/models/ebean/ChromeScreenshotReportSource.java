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
package models.ebean;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

/**
 * Data Model for SQL storage of a chrome screenshot based report generation scheme.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@DiscriminatorValue("chromeScreenshot")
public class ChromeScreenshotReportSource extends ReportSource {
    @Column(name = "url")
    private String url;

    @Column(name = "title")
    private String title;

    @Column(name = "ignore_certificate_errors")
    private boolean ignoreCertificateErrors;

    @Column(name = "triggering_event_name")
    private String triggeringEventName;

    @Column(name = "pdf_width_inches")
    private double pdfWidthInches;

    @Column(name = "pdf_height_inches")
    private double pdfHeightInches;

    public String getUrl() {
        return url;
    }

    public void setUrl(final String value) {
        url = value;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String value) {
        title =  value;
    }

    public boolean isIgnoreCertificateErrors() {
        return ignoreCertificateErrors;
    }

    public void setIgnoreCertificateErrors(final boolean value) {
        ignoreCertificateErrors = value;
    }

    public String getTriggeringEventName() {
        return triggeringEventName;
    }

    public void setTriggeringEventName(final String value) {
        triggeringEventName = value;
    }

    public double getPdfWidthInches() {
        return pdfWidthInches;
    }

    public void setPdfWidthInches(final double value) {
        pdfWidthInches = value;
    }

    public double getPdfHeightInches() {
        return pdfHeightInches;
    }

    public void setPdfHeightInches(final double value) {
        pdfHeightInches = value;
    }

    @Override
    public void toInternal() {
        // TODO(cbriones): Implement this
    }
}
// CHECKSTYLE.ON: MemberNameCheck
