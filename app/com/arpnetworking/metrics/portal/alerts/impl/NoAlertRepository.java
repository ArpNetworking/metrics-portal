/*
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
package com.arpnetworking.metrics.portal.alerts.impl;

import com.arpnetworking.logback.annotations.LogValue;
import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.steno.LogValueMapFactory;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.inject.Inject;
import models.internal.Alert;
import models.internal.AlertQuery;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultAlertQuery;
import models.internal.impl.DefaultQueryResult;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of empty alert repository.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public final class NoAlertRepository implements AlertRepository {

    /**
     * Public constructor.
     */
    @Inject
    public NoAlertRepository() {}

    @Override
    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening alert repository").log();
        _isOpen.set(true);
    }

    @Override
    public void close() {
        assertIsOpen();
        LOGGER.debug().setMessage("Closing alert repository").log();
        _isOpen.set(false);
    }

    @Override
    public Optional<Alert> get(final UUID identifier, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Getting alert")
                .addData("identifier", identifier)
                .addData("organization", organization)
                .log();
        return Optional.empty();
    }

    @Override
    public AlertQuery createQuery(final Organization organization) {
        LOGGER.debug()
                .setMessage("Preparing query")
                .addData("organization", organization)
                .log();
        return new DefaultAlertQuery(this, organization);
    }

    @Override
    public QueryResult<Alert> query(final AlertQuery query) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Querying")
                .addData("query", query)
                .log();
        return new DefaultQueryResult<>(Collections.<Alert>emptyList(), 0);
    }

    @Override
    public long getAlertCount(final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Getting alert count")
                .addData("organization", organization)
                .log();
        return 0;
    }

    @Override
    public void addOrUpdateAlert(final Alert alert, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Add or update alert")
                .addData("alert", alert)
                .addData("organization", organization)
                .log();
    }

    @Override
    public int delete(final UUID identifier, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Delete alert")
                .addData("identifier", identifier)
                .addData("organization", organization)
                .log();
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

    @Override
    public String toString() {
        return toLogValue().toString();
    }

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_isOpen.get() != expectedState) {
            throw new IllegalStateException(String.format("Alert repository is not %s", expectedState ? "open" : "closed"));
        }
    }

    private final AtomicBoolean _isOpen = new AtomicBoolean(false);

    private static final Logger LOGGER = LoggerFactory.getLogger(NoAlertRepository.class);
}
