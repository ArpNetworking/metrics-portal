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
package models.view.reports;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import models.internal.impl.DefaultEmailRecipient;

import java.util.UUID;

/**
 * View model for a {@link models.internal.reports.Recipient}.
 *
 * Currently the view model only specifies a single format for a recipient even though the internal
 * model supports many.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = EmailRecipient.class, name = "Email"),
})
public abstract class Recipient {
    public UUID getId() {
        return _id;
    }

    public void setId(final UUID id) {
        this._id = id;
    }

    public String getAddress() {
        return _address;
    }

    public void setAddress(final String address) {
        this._address = address;
    }

    public ReportFormat getFormat() {
        return _format;
    }

    public void setFormat(final ReportFormat format) {
        this._format = format;
    }

    /**
     * Convert this view model into its internal representation.
     *
     * @return The internal model for this Recipient.
     */
    public abstract models.internal.reports.Recipient toInternal();

    static Recipient fromInternal(final models.internal.reports.Recipient recipient, final ReportFormat format) {
        final models.internal.reports.Recipient.Visitor<Recipient> visitor = new models.internal.reports.Recipient.Visitor<Recipient>() {
            @Override
            public Recipient visit(final DefaultEmailRecipient emailRecipient) {
                return EmailRecipient.fromInternal(emailRecipient, format);
            }
        };
        return visitor.visit(recipient);
    }

    @JsonProperty("id")
    private UUID _id;
    @JsonProperty("address")
    private String _address;
    @JsonProperty("format")
    private ReportFormat _format;
}
