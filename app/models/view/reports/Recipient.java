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

import com.arpnetworking.metrics.portal.reports.RecipientType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.UUID;

/**
 * View model for a {@link models.internal.reports.Recipient}.
 *
 * Currently the view model only specifies a single format for a recipient even though the internal
 * model supports many.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class Recipient {
    public UUID getId() {
        return _id;
    }

    public void setId(final UUID id) {
        this._id = id;
    }

    public RecipientType getType() {
        return _type;
    }

    public void setType(final RecipientType type) {
        this._type = type;
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
    public models.internal.reports.Recipient toInternal() {
        return new models.internal.impl.DefaultRecipient.Builder()
                .setId(_id)
                .setType(_type)
                .setAddress(_address)
                .build();
    }

    static Recipient fromInternal(final models.internal.reports.Recipient recipient, final ReportFormat format) {
        final Recipient result = new Recipient();
        result.setId(recipient.getId());
        result.setType(recipient.getType());
        result.setAddress(recipient.getAddress());
        result.setFormat(format);
        return result;
    }

    private UUID _id;
    private RecipientType _type;
    private String _address;
    private ReportFormat _format;
}
