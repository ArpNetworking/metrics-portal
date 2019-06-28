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

package models.internal.reports;

import com.arpnetworking.logback.annotations.Loggable;
import com.arpnetworking.metrics.portal.reports.RecipientType;

import java.util.UUID;

/**
 * Internal model for a recipient, i.e. a destination.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@Loggable
public interface Recipient {
    /**
     * Get the unique identifier of this recipient.
     *
     * @return the id of this recipient
     */
    UUID getId();

    /**
     * Get the {@link RecipientType} of this recipient.
     *
     * @return the address of this recipient
     */
    RecipientType getType();

    /**
     * Get the address of this recipient.
     *
     * @return the address of this recipient
     */
    String getAddress();
}
