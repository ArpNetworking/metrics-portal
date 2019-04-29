/*
 * Copyright 2018 Smartsheet.com
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
package com.arpnetworking.metrics.portal.organizations.impl;

import com.arpnetworking.metrics.portal.organizations.OrganizationRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.ImmutableList;
import models.internal.Organization;
import models.internal.OrganizationQuery;
import models.internal.QueryResult;
import models.internal.impl.DefaultOrganization;
import models.internal.impl.DefaultOrganizationQuery;
import models.internal.impl.DefaultQueryResult;
import play.mvc.Http;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Always returns the default organization.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 * @author Ville Koskela (vkoskela at dropbox dot com)
 */
public final class DefaultOrganizationRepository implements OrganizationRepository {

    @Override
    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening organization repository").log();
        _isOpen.set(true);
    }

    @Override
    public void close() {
        assertIsOpen();
        LOGGER.debug().setMessage("Closing organization repository").log();
        _isOpen.set(false);
    }

    @Override
    public Organization get(final Http.Request request) {
        // TODO(Ville): Replace the stub implementation.
        // This code should extract the organization identifier from the session.
        return get(DEFAULT.getId());
    }

    @Override
    public Organization get(final UUID id) {
        assertIsOpen();
        if (DEFAULT.getId().equals(id)) {
            return DEFAULT;
        }
        throw new NoSuchElementException(String.format("The organization does not exist with id: %s", id));
    }

    @Override
    public OrganizationQuery createQuery() {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Preparing query")
                .log();
        return new DefaultOrganizationQuery(this);
    }

    @Override
    public QueryResult<Organization> query(final OrganizationQuery query) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Querying")
                .addData("query", query)
                .log();
        return new DefaultQueryResult<>(
                ImmutableList.of(DEFAULT),
                1,
                String.valueOf(DEFAULT.getId().hashCode()));
    }

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_isOpen.get() != expectedState) {
            throw new IllegalStateException(String.format("Organization repository is not %s", expectedState ? "open" : "closed"));
        }
    }

    private final AtomicBoolean _isOpen = new AtomicBoolean(false);

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultOrganizationRepository.class);
    private static final Organization DEFAULT = new DefaultOrganization.Builder()
            .setId(UUID.fromString("0eb03110-2a36-4cb1-861f-7375afc98b9b"))
            .build();
}
