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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;

import java.io.Serializable;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;

/**
 * Utility class to help split {@link RollupDefinition}s into pieces.
 *
 * @author Spencer Pearson (spencerpearson@dropbox.com)
 */
public class RollupPartitioner {

    private static final Duration TIMEOUT_HEURISTIC_THRESHOLD = Duration.ofSeconds(30);

    /**
     * Public constructor.
     */
    public RollupPartitioner() {}

    /**
     * Split a {@link RollupDefinition} into a family of cheaper-to-execute {@link RollupDefinition}s which,
     * run independently, will have an effect equivalent the the input's.
     *
     * @param job the {@link RollupDefinition} to split up
     * @return a set of cheaper jobs which partition the parent's work among them
     * @throws CannotSplitException if there is no way to split the given job into multiple cheaper ones
     */
    public ImmutableSet<RollupDefinition> splitJob(final RollupDefinition job) throws CannotSplitException {
        final ImmutableMap<String, String> filterTags = job.getFilterTags();
        final ImmutableMultimap<String, String> allTags = job.getAllMetricTags();
        final Optional<String> nextFilterTag = allTags.keySet().stream()
                .filter(tag -> !filterTags.containsKey(tag)) // already filtered on
                .filter(tag -> allTags.get(tag).size() > 1) // no point splitting on a single-value tag
                .min(Comparator.comparing(
                        // Ad-hoc, not-super-principled attempt to balance the per-query overhead of splitting too much
                        //   against the risk of splitting too little and having the sub-jobs time out:
                        //   take the tag whose number of values is closest to 10, in log-space.
                        //     (graph: https://www.desmos.com/calculator/vpr78hofys )
                        tag -> Math.abs(Math.log10(allTags.get(tag).size()) - 1)
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

    /**
     * Given a failure from a {@link RollupDefinition}-execution, guess whether splitting the job into smaller pieces might help.
     *
     * Merely a heuristic: false positives and false negatives are entirely possible.
     *
     * @param failure the failure that occurred when executing the {@link RollupDefinition}
     * @return a guess at whether splitting the job into smaller pieces might help
     */
    public boolean mightSplittingFixFailure(final Throwable failure) {
        if (!(failure instanceof KairosDbRequestException)) {
            final Throwable cause = failure.getCause();
            return cause != null && mightSplittingFixFailure(cause);
        }

        // TODO(spencerpearson): I hate how ad-hoc this is, but Kairos's behavior when it barfs
        //   halfway through a rollup is _also_ ad-hoc: it sometimes 500s, sometimes 502s.

        final KairosDbRequestException kdbFailure = (KairosDbRequestException) failure;

        final boolean wasLong = !kdbFailure.getRequestDuration().minus(TIMEOUT_HEURISTIC_THRESHOLD).isNegative();
        final int statusGroup = kdbFailure.getHttpStatus() / 100 * 100;

        return wasLong && statusGroup == 500;
    }

    /**
     * Exception to indicate that a job cannot be split into sub-jobs.
     */
    public static final class CannotSplitException extends Exception implements Serializable {
        private static final long serialVersionUID = 7426023317765009608L;
        private final RollupDefinition _job;

        public RollupDefinition getJob() {
            return _job;
        }

        CannotSplitException(final RollupDefinition job) {
            super(job.toString());
            _job = job;
        }
    }
}
