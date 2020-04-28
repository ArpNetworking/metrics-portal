/*
 * Copyright 2020 Dropbox Inc.
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
package com.arpnetworking.rollups;

import com.arpnetworking.kairos.client.KairosDbRequestException;
import com.arpnetworking.metrics.apachehttpsinkextra.shaded.org.apache.http.HttpStatus;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;

import java.util.Comparator;
import java.util.Optional;

/**
 * TODO(spencerpearson)
 *
 * @author Spencer Pearson (spencerpearson@dropbox.com)
 */
public final class RollupPartitioningUtils {
    public static ImmutableSet<RollupDefinition> splitJob(final RollupDefinition job) throws CannotSplitException {
        final ImmutableMap<String, String> filterTags = job.getFilterTags();
        final ImmutableMultimap<String, String> allTags = job.getAllMetricTags();
        final Optional<String> nextFilterTag = allTags.keySet().stream()
                .filter(tag -> !filterTags.containsKey(tag)) // already filtered on
                .filter(tag -> allTags.get(tag).size() > 1) // no point splitting on a single-value tag
                .max(Comparator.comparing(
                        tag -> allTags.get(tag).size()
                ));

        if (!nextFilterTag.isPresent()) {
            throw new CannotSplitException(job);
        }

        return allTags.get(nextFilterTag.get()).stream()
                .map(tagValue -> RollupDefinition.Builder.<RollupDefinition, RollupDefinition.Builder>clone(job)
                        .setFilterTags(ImmutableMap.<String, String>builder()
                                .putAll(filterTags)
                                .put(nextFilterTag.get(), tagValue)
                                .build())
                        .build()
                )
                .collect(ImmutableSet.toImmutableSet());
    }

    public static boolean mightSplittingFixFailure(final Throwable failure) {
        if (failure instanceof KairosDbRequestException) {
            final int status = ((KairosDbRequestException) failure).getHttpStatus();
            return (status == HttpStatus.SC_BAD_GATEWAY
                    || status == HttpStatus.SC_SERVICE_UNAVAILABLE ||
                    status == HttpStatus.SC_GATEWAY_TIMEOUT);
        }
        return false;
    }

    public static final class CannotSplitException extends Exception {
        public final RollupDefinition job;
        public CannotSplitException(final RollupDefinition job) {
            super(job.toString());
            this.job = job;
        }
    }
}
