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

import com.arpnetworking.commons.builder.OvalBuilder;
import net.sf.oval.constraint.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * Iterator that wraps a paging function, turning a "yield batches of elements at once" interface into a plain {@link Iterator}.
 *
 * A "paging function" is, conceptually, a view into some ordered list of elements.
 * It's a function that takes an integer "offset" and returns a {@link List} containing some of the elements immediately after that offset
 * (or an empty list if-and-only-if the offset is {@code >=} the number of elements).
 *
 * For example, if you have a database full of {@code Employee}s, and you want to iterate over all of them,
 * fetching batches of 100 at a time from the database, your paging function might look like
 * <pre>
 *     offset -> db.query("SELECT * FROM Employees ORDER BY created_date LIMIT 100 OFFSET ?", offset).getValues()
 * </pre>
 *
 * @param <E> The type of element yielded by the paging function.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class PagingIterator<E> implements Iterator<E> {

    private final Function<Integer, List<? extends E>> _getPage;
    private Iterator<? extends E> _buffer = new ArrayList<E>().iterator();
    private int _offset = 0;
    private boolean _exhaustedSource = false;

    private PagingIterator(final Builder<E> builder) {
        _getPage = builder._getPage;
    }

    private void repopulateBufferIfNeeded() {
        if (_exhaustedSource) {
            return;
        }
        if (_buffer.hasNext()) {
            return;
        }
        synchronized (this) {
            final List<? extends E> results = _getPage.apply(_offset);
            _buffer = results.iterator();
            _offset += results.size();
        }
        _exhaustedSource = !_buffer.hasNext();
    }

    @Override
    public boolean hasNext() {
        repopulateBufferIfNeeded();
        return _buffer.hasNext();
    }

    @Override
    public E next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return _buffer.next();
    }

    /**
     * Implementation of builder pattern for {@link PagingIterator}.
     *
     * @param <E> The type of element to be iterated over.
     *
     * @author Spencer Pearson (spencerpearson at dropbox dot com)
     */
    public static final class Builder<E> extends OvalBuilder<PagingIterator<E>> {
        @NotNull
        private Function<Integer, List<? extends E>> _getPage;

        /**
         * Public constructor.
         */
        public Builder() {
            super(PagingIterator<E>::new);
        }

        /**
         * The paging function. Required. Must not be null.
         *
         * @param getPage The paging function (see {@link PagingIterator}).
         * @return This instance of Builder.
         */
        public Builder<E> setGetPage(final Function<Integer, List<? extends E>> getPage) {
            _getPage = getPage;
            return this;
        }
    }
}
