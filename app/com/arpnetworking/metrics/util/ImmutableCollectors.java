/**
 * Copyright 2017 Smartsheet
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
package com.arpnetworking.metrics.util;

import com.google.common.collect.ImmutableList;

import java.util.stream.Collector;

/**
 * Collectors for building immutable collections.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class ImmutableCollectors {
    /**
     * Collects to an Immutable list.
     * @param <T> The type of the elements
     * @return A new ImmutableList with the collected elements
     */
    @SuppressWarnings("unchecked")
    public static <T> Collector<T, ImmutableList.Builder<T>, ImmutableList<T>> toList() {
        return (Collector<T, ImmutableList.Builder<T>, ImmutableList<T>>) IMMUTABLE_LIST_COLLECTOR;
    }

    private ImmutableCollectors() { }

    @SuppressWarnings("rawtypes")
    private static final Collector IMMUTABLE_LIST_COLLECTOR =
            Collector.of(
                    ImmutableList.Builder::new,
                    ImmutableList.Builder::add,
                    (a, b) -> a.addAll(b.build()),
                    ImmutableList.Builder::build);
}
