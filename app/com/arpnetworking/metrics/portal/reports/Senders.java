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

package com.arpnetworking.metrics.portal.reports;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import models.internal.reports.Recipient;
import models.internal.reports.ReportFormat;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    public static CompletionStage<Void> sendAll(
            final Injector injector,
            final ImmutableMultimap<Recipient, ReportFormat> recipientToFormats,
            final ImmutableMap<ReportFormat, RenderedReport> formatToRendered
    ) {
        final ImmutableMultimap<RecipientType, Recipient> recipientTypeGroups = partition(recipientToFormats.keySet(), Recipient::getType);
        final CompletableFuture<?>[] futures = recipientTypeGroups
                .asMap()
                .entrySet()
                .stream()
                .map(entry -> {
                    final Sender sender = injector.getInstance(Key.get(Sender.class, Names.named(entry.getKey().name())));
                    return sender.send(mask(recipientToFormats, entry.getValue()), formatToRendered).toCompletableFuture();
                })
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    /**
     * Restrict {@code map} to key/value pairs whose keys are in {@code keys}.
     */
    private static <K, V> ImmutableMultimap<K, V> mask(final ImmutableMultimap<K, V> map, final Collection<K> keys) {
        return ImmutableMultimap.copyOf(
                map
                        .entries()
                        .stream()
                        .filter(entry -> keys.contains(entry.getKey()))
                        .collect(Collectors.toList())
        );
    }

    private static <K, V> ImmutableMultimap<K, V> partition(final Collection<V> values, final Function<V, K> getKey) {
        ImmutableMultimap.Builder<K, V> builder = new ImmutableMultimap.Builder<>();
        for (V value : values) {
            builder = builder.put(getKey.apply(value), value);
        }
        return builder.build();
    }


    private Senders() {}
}
