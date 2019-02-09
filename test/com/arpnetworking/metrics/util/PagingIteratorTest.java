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
package com.arpnetworking.metrics.util;

import com.google.common.collect.Lists;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link PagingIterator}.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public class PagingIteratorTest {

    private static Function<Integer, List<? extends Integer>> rangePager(final int pageSize, final int limit) {
        return offset -> {
            final List<Integer> result = new ArrayList<>(pageSize);
            for (int i = offset; i < offset + pageSize && i < limit; i++) {
                result.add(i);
            }
            return result;
        };
    }

    @Test
    public void testEvenlyDivisible() {
        final List<Integer> expected = Lists.newArrayList(0, 1, 2, 3, 4, 5);

        final Function<Integer, List<? extends Integer>> getPage = Mockito.spy(new MockableFunction<>(rangePager(3, 6)));
        final List<Integer> actual = Lists.newArrayList(new PagingIterator.Builder<Integer>().setGetPage(getPage).build());
        assertEquals(expected, actual);

        for (int expectedGetPageCall : new int[]{0, 3, 6}) {
            Mockito.verify(getPage).apply(expectedGetPageCall);
        }
        for (int unexpectedGetPageCall : new int[]{1, 2, 4, 5, 7, 8, 9, 10, 11, 12, 13}) {
            Mockito.verify(getPage, Mockito.never()).apply(unexpectedGetPageCall);
        }
    }

    @Test
    public void testNotEvenlyDivisible() {
        final List<Integer> expected = Lists.newArrayList(0, 1, 2, 3, 4, 5);

        final Function<Integer, List<? extends Integer>> getPage = Mockito.spy(new MockableFunction<>(rangePager(4, 6)));
        final List<Integer> actual = Lists.newArrayList(new PagingIterator.Builder<Integer>().setGetPage(getPage).build());
        assertEquals(expected, actual);

        for (int expectedGetPageCall : new int[]{0, 4, 6}) {
            Mockito.verify(getPage).apply(expectedGetPageCall);
        }
        for (int unexpectedGetPageCall : new int[]{1, 2, 3, 5, 7, 8, 9, 10, 11, 12, 13}) {
            Mockito.verify(getPage, Mockito.never()).apply(unexpectedGetPageCall);
        }
    }

    @Test
    public void testEmpty() {
        final List<Integer> expected = new ArrayList<>();
        final Function<Integer, List<? extends Integer>> getPage = Mockito.spy(new MockableFunction<>(offset -> new ArrayList<>()));
        final List<Integer> actual = Lists.newArrayList(new PagingIterator.Builder<Integer>().setGetPage(getPage).build());
        assertEquals(actual, expected);
        Mockito.verify(getPage).apply(0);
        Mockito.verify(getPage, Mockito.never()).apply(1);
    }

    private static class MockableFunction<T, R> implements Function<T, R> {
        private final Function<T, R> _wrapped;

        MockableFunction(final Function<T, R> wrapped) {
            _wrapped = wrapped;
        }

        @Override
        public R apply(final T t) {
            return _wrapped.apply(t);
        }
    }

}
