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
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * View model for a report source that pulls screenshots from the web.
 *
 * Play view models are mutable.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@Loggable
public final class WebPageReportSource implements ReportSource {

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

    public Duration getTimeout() {
        return _timeout;
    }

    public void setTimeout(final Duration value) {
        _timeout = value;
    }

    public String getJsRunOnLoad() {
        return _jsRunOnLoad;
    }

    public void setJsRunOnLoad(final String value) {
        _jsRunOnLoad = value;
    }

    public String getTriggeringEventName() {
        return _triggeringEventName;
    }

    public void setTriggeringEventName(final String value) {
        _triggeringEventName = value;
    }

    @Override
    public models.internal.impl.WebPageReportSource toInternal() {
        return new models.internal.impl.WebPageReportSource.Builder()
                .setId(_id)
                .setUri(_uri)
                .setTitle(_title)
                .setIgnoreCertificateErrors(_ignoreCertificateErrors)
                .setTimeout(_timeout)
                .setJsRunOnLoad(_jsRunOnLoad)
                .setTriggeringEventName(_triggeringEventName)
                .build();
    }

    /**
     * Create a {@code WebPageReportSource} from its internal representation.
     *
     * @param source The internal model.
     * @return The view model.
     */
    public static WebPageReportSource fromInternal(final models.internal.impl.WebPageReportSource source) {
        final WebPageReportSource viewSource = new WebPageReportSource();
        viewSource.setId(source.getId());
        viewSource.setUri(source.getUri());
        viewSource.setTitle(source.getTitle());
        viewSource.setTriggeringEventName(source.getTriggeringEventName());
        viewSource.setTimeout(source.getTimeout());
        viewSource.setJsRunOnLoad(source.getJsRunOnLoad());
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
                .add("timeout", _timeout)
                .add("jsRunOnLoad", _jsRunOnLoad)
                .add("triggeringEventName", _triggeringEventName)
                .toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WebPageReportSource)) {
            return false;
        }
        final WebPageReportSource otherWebPageReportSource = (WebPageReportSource) o;
        return _ignoreCertificateErrors == otherWebPageReportSource._ignoreCertificateErrors
                && Objects.equals(_id, otherWebPageReportSource._id)
                && Objects.equals(_uri, otherWebPageReportSource._uri)
                && Objects.equals(_title, otherWebPageReportSource._title)
                && Objects.equals(_timeout, otherWebPageReportSource._timeout)
                && Objects.equals(_jsRunOnLoad, otherWebPageReportSource._jsRunOnLoad)
                && Objects.equals(_triggeringEventName, otherWebPageReportSource._triggeringEventName);
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
    @JsonProperty("timeout")
    private Duration _timeout;
    @JsonProperty("jsRunOnLoad")
    private String _jsRunOnLoad;
    @JsonProperty("triggeringEventName")
    private String _triggeringEventName;
}
