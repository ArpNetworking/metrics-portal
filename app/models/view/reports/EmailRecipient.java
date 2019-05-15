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

import models.internal.impl.DefaultRecipient;

/**
 * View model for a recipient specified by an email address.
 *
 * See {@link DefaultRecipient} for the equivalent internal model.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class EmailRecipient extends Recipient {
    /**
     * Create a view model from an internal model.
     *
     * @param recipient The internal model.
     * @param format The format to use for this recipient.
     * @return The view model.
     */
    public static EmailRecipient fromInternal(final DefaultRecipient recipient, final ReportFormat format) {
        final EmailRecipient viewRecipient = new EmailRecipient();
        viewRecipient.setId(recipient.getId());
        viewRecipient.setType(recipient.getType());
        viewRecipient.setAddress(recipient.getAddress());
        viewRecipient.setFormat(format);
        return viewRecipient;
    }

    /**
     * Create an internal model from this view model.
     *
     * @return The internal model corresponding to this view.
     */
    public DefaultRecipient toInternal() {
        return new DefaultRecipient.Builder()
                .setId(getId())
                .setType(getType())
                .setAddress(getAddress())
                .build();
    }
}
