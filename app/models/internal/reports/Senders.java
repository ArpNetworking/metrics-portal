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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;

import java.util.concurrent.CompletionStage;

/**
 * Utilities for sending reports.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class Senders {
    /**
     * TODO(spencerpearson).
     * @param injector TODO(spencerpearson).
     * @param recipientToFormats TODO(spencerpearson).
     * @param formatToRendered TODO(spencerpearson).
     * @return TODO(spencerpearson).
     */
    public static CompletionStage<Void> send(
            final Injector injector,
            final ImmutableMultimap<Recipient, ReportFormat> recipientToFormats,
            final ImmutableMap<ReportFormat, RenderedReport> formatToRendered
    ) {
        final String typeName = recipientToFormats.keySet().iterator().next().getType().name();
        final Sender sender = injector.getInstance(Key.get(Sender.class, Names.named(typeName)));
        return sender.send(recipientToFormats, formatToRendered);
    }

    private Senders() {}
}
