/**
 * Copyright 2015 Groupon.com
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
package com.arpnetworking.metrics.portal.expressions.impl;

import com.arpnetworking.logback.annotations.LogValue;
import com.arpnetworking.steno.LogValueMapFactory;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.inject.Inject;
import models.internal.Expression;
import models.internal.ExpressionQuery;
import models.internal.QueryResult;
import models.internal.impl.DefaultExpressionQuery;
import models.internal.impl.DefaultQueryResult;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of empty expression repository.
 *
 * @author Ville Koskela (vkoskela at groupon dot com)
 */
public final class NoExpressionRepository extends ReadOnlyAbstractExpressionRepository {

    /**
     * Public constructor.
     */
    @Inject
    public NoExpressionRepository() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening expression repository").log();
        _isOpen.set(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        assertIsOpen();
        LOGGER.debug().setMessage("Closing expression repository").log();
        _isOpen.set(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Expression> get(final UUID identifier) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Getting expression")
                .addData("identifier", identifier)
                .log();
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExpressionQuery createQuery() {
        LOGGER.debug().setMessage("Preparing query").log();
        return new DefaultExpressionQuery(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public QueryResult<Expression> query(final ExpressionQuery query) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Querying")
                .addData("query", query)
                .log();
        return new DefaultQueryResult<>(Collections.<Expression>emptyList(), 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getExpressionCount() {
        assertIsOpen();
        LOGGER.debug().setMessage("Getting expression count").log();
        return 0;
    }

    /**
     * Generate a Steno log compatible representation.
     *
     * @return Steno log compatible representation.
     */
    @LogValue
    public Object toLogValue() {
        return LogValueMapFactory.builder(this)
                .put("isOpen", _isOpen)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toLogValue().toString();
    }

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_isOpen.get() != expectedState) {
            throw new IllegalStateException(String.format("Expression repository is not %s", expectedState ? "open" : "closed"));
        }
    }

    private final AtomicBoolean _isOpen = new AtomicBoolean(false);

    private static final Logger LOGGER = LoggerFactory.getLogger(NoExpressionRepository.class);
}
