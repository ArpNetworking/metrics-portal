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

import models.internal.impl.DefaultRecipient;

import java.util.UUID;

/**
 * Internal model for a recipient, i.e. a destination.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public interface Recipient {
    /**
     * Get the unique identifier of this recipient.
     *
     * @return the id of this recipient
     */
    UUID getId();

    /**
     * Get the address of this recipient.
     *
     * @return the address of this recipient
     */
    String getAddress();

    /**
     * Applies a {@code Visitor} to this recipient. This should delegate the to the appropriate {@code Visitor#visit} overload.
     *
     * @param recipientVisitor the visitor
     * @param <T> the return type of the visitor. Use {@link Void} for visitors that do not need to return a result.
     * @return The result of applying the visitor.
     */
    <T> T accept(Visitor<T> recipientVisitor);

    /**
     * {@code Visitor} abstracts over operations which could potentially handle various
     * implementations of {@code Recipient}.
     *
     * @param <T> the return type of the visitor.
     */
    interface Visitor<T> {
        /**
         * Visit an {@code DefaultRecipient}.
         *
         * @param emailRecipient The recipient to visit.
         * @return The result of applying the visitor.
         */
        T visit(DefaultRecipient emailRecipient);

        /**
         * Convenience method equivalent to {@code recipient.accept(this) }.
         *
         * @param recipient The recipient to visit.
         * @return The result of applying the visitor
         */
        default T visit(Recipient recipient) {
            return recipient.accept(this);
        }
    }
}
