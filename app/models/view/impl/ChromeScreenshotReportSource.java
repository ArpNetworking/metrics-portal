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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import models.view.reports.ReportSource;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

/**
 * View model for a report source that pulls screenshots from a Chrome web browser.
 *
 * Play view models are mutable.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@Loggable
public final class ChromeScreenshotReportSource implements ReportSource {

    public UUID getId() {
        return _id;
    }

    public void setId(final UUID value) {
        _id = value;
    }

    public URI getUri() {
        return _uri;
    }

    public void setUri(final URI value) {
        _uri = value;
    }

    public String getTitle() {
        return _title;
    }

    public void setTitle(final String value) {
        _title = value;
    }

    public boolean getIgnoreCertificateErrors() {
        return _ignoreCertificateErrors;
    }

    public void setIgnoreCertificateErrors(final boolean value) {
        _ignoreCertificateErrors = value;
    }

    public String getTriggeringEventName() {
        return _triggeringEventName;
    }

    public void setTriggeringEventName(final String value) {
        _triggeringEventName = value;
    }

    @Override
    public models.internal.impl.ChromeScreenshotReportSource toInternal() {
        return new models.internal.impl.ChromeScreenshotReportSource.Builder()
                .setId(_id)
                .setUri(_uri)
                .setTitle(_title)
                .setIgnoreCertificateErrors(_ignoreCertificateErrors)
                .setTriggeringEventName(_triggeringEventName)
                .build();
    }

    /**
     * Create a {@code ChromeScreenshotReportSource} from its internal representation.
     *
     * @param source The internal model.
     * @return The view model.
     */
    public static ChromeScreenshotReportSource fromInternal(final models.internal.impl.ChromeScreenshotReportSource source) {
        final ChromeScreenshotReportSource viewSource = new ChromeScreenshotReportSource();
        viewSource.setId(source.getId());
        viewSource.setUri(source.getUri());
        viewSource.setTitle(source.getTitle());
        viewSource.setTriggeringEventName(source.getTriggeringEventName());
        viewSource.setIgnoreCertificateErrors(source.ignoresCertificateErrors());
        return viewSource;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", _id)
                .add("url", _uri)
                .add("title", _title)
                .add("ignoreCertificateErrors", _ignoreCertificateErrors)
                .add("triggeringEventName", _triggeringEventName)
                .toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChromeScreenshotReportSource)) {
            return false;
        }
        final ChromeScreenshotReportSource otherChromeScreenshotReportSource = (ChromeScreenshotReportSource) o;
        return _ignoreCertificateErrors == otherChromeScreenshotReportSource._ignoreCertificateErrors
                && Objects.equals(_id, otherChromeScreenshotReportSource._id)
                && Objects.equals(_uri, otherChromeScreenshotReportSource._uri)
                && Objects.equals(_title, otherChromeScreenshotReportSource._title)
                && Objects.equals(_triggeringEventName, otherChromeScreenshotReportSource._triggeringEventName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_id, _uri, _title, _ignoreCertificateErrors, _triggeringEventName);
    }


    @JsonProperty("id")
    private UUID _id;
    @JsonProperty("uri")
    private URI _uri;
    @JsonProperty("title")
    private String _title;
    @JsonProperty("ignoreCertificateErrors")
    private boolean _ignoreCertificateErrors;
    @JsonProperty("triggeringEventName")
    private String _triggeringEventName;
}
