/*
 * Copyright 2020 Dropbox, Inc.
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

import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import models.internal.AlertQuery;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.alerts.Alert;
import models.internal.impl.DefaultAlertQuery;
import models.internal.impl.DefaultOrganization;
import models.internal.impl.DefaultQueryResult;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * An immutable, in-memory alert repository.
 *
 * @author Christian Briones (cbriones at dropbox dot com).
 * @apiNote This repository is read-only, so any additions, deletions, or updates will result in an {@link
 * UnsupportedOperationException}.
 */
public class MapAlertRepository implements AlertRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapAlertRepository.class);
    private final AtomicBoolean _isOpen = new AtomicBoolean(false);
    private final Organization _organization;
    private final Supplier<Collection<Alert>> _alertSupplier;
    private ImmutableMap<UUID, Alert> _alerts = ImmutableMap.of();

    /**
     * Create an empty repository.
     *
     * @param org The organization to group the alerts under.
     */
    @Inject
    public MapAlertRepository(
            final UUID org
    ) {
        this(org, ImmutableList::of);
    }

    /**
     * Create a repository that is initially populated using the given supplier.
     *
     * The supplier will be called once the repository is opened.
     *
     * @param org The organization to group the alerts under.
     * @param alertSupplier The initial set of alerts.
     */
    public MapAlertRepository(
            final UUID org,
            final Supplier<Collection<Alert>> alertSupplier
    ) {
        _organization = new DefaultOrganization.Builder().setId(org).build();
        _alertSupplier = alertSupplier;
    }

    @Override
    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening MapAlertRepository").log();
        _alerts = _alertSupplier.get()
                .stream()
                .collect(ImmutableMap.toImmutableMap(
                        Alert::getId,
                        a -> a
                ));
        _isOpen.set(true);
    }

    @Override
    public void close() {
        assertIsOpen();
        LOGGER.debug().setMessage("Closing MapAlertRepository").log();
        _isOpen.set(false);
    }

    @Override
    public Optional<Alert> getAlert(final UUID identifier, final Organization organization) {
        assertIsOpen();
        return Optional.ofNullable(_alerts.get(identifier));
    }

    @Override
    public AlertQuery createAlertQuery(final Organization organization) {
        assertIsOpen();
        return new DefaultAlertQuery(this, organization);
    }

    @Override
    public QueryResult<Alert> queryAlerts(final AlertQuery query) {
        assertIsOpen();
        final Predicate<Alert> containsPredicate =
                query.getContains()
                        .map(c -> (Predicate<Alert>) a -> a.getDescription().contains(c))
                        .orElse(e -> true);

        final ImmutableList<Alert> alerts = _alerts.values().stream()
                .filter(containsPredicate)
                .skip(query.getOffset().orElse(0))
                .limit(query.getOffset().orElse(1000))
                .collect(ImmutableList.toImmutableList());

        final long total = _alerts.values().stream()
                .filter(containsPredicate)
                .count();

        return new DefaultQueryResult<>(alerts, total);
    }

    @Override
    public long getAlertCount(final Organization organization) {
        assertIsOpen();
        return _alerts.size();
    }

    /* Unsupported mutation operations */

    @Override
    public int deleteAlert(final UUID identifier, final Organization organization) {
        // Since we expect to use this repository just as every other AlertRepository,
        // we should enforce the open-before-use invariant rather than immediately
        // throwing on mutations.
        assertIsOpen();
        throw new UnsupportedOperationException("MapAlertRepository is read-only");
    }

    @Override
    public void addOrUpdateAlert(final Alert alert, final Organization organization) {
        assertIsOpen();
        throw new UnsupportedOperationException("MapAlertRepository is read-only");
    }

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_isOpen.get() != expectedState) {
            throw new IllegalStateException(String.format("MapAlertRepository is not %s",
                    expectedState ? "open" : "closed"));
        }
    }
}
