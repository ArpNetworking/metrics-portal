/*
 * Copyright 2014 Groupon.com
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
package com.arpnetworking.metrics.portal.hosts.impl;

import com.arpnetworking.logback.annotations.LogValue;
import com.arpnetworking.metrics.portal.hosts.HostRepository;
import com.arpnetworking.steno.LogValueMapFactory;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import jakarta.inject.Inject;
import models.internal.Host;
import models.internal.HostQuery;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.impl.DefaultHostQuery;
import models.internal.impl.DefaultQueryResult;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This is a dummy implementation of {@link HostRepository}.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public final class NoHostRepository implements HostRepository {

    /**
     * Public constructor.
     */
    @Inject
    public NoHostRepository() {}

    @Override
    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening host repository").log();
        _isOpen.set(true);
    }

    @Override
    public void close() {
        assertIsOpen();
        LOGGER.debug().setMessage("Closing host repository").log();
        _isOpen.set(false);
    }

    @Override
    public Optional<Host> getHost(final String hostname, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Getting host")
                .addData("hostname", hostname)
                .addData("organization", organization)
                .log();
        return Optional.empty();
    }

    @Override
    public void addOrUpdateHost(final Host host, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Adding or updating host")
                .addData("host", host)
                .addData("organization", organization)
                .log();
    }

    @Override
    public void deleteHost(final String hostname, final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Deleting host")
                .addData("hostname", hostname)
                .addData("organization", organization)
                .log();
    }


    @Override
    public HostQuery createHostQuery(final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Preparing query")
                .addData("organization", organization)
                .log();
        return new DefaultHostQuery(this, organization);
    }

    @Override
    public QueryResult<Host> queryHosts(final HostQuery query) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Querying")
                .addData("query", query)
                .log();
        return new DefaultQueryResult<>(Collections.<Host>emptyList(), 0);
    }

    @Override
    public long getHostCount(final Organization organization) {
        assertIsOpen();
        LOGGER.debug()
                .setMessage("Getting host count")
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
            throw new IllegalStateException(String.format("Host repository is not %s", expectedState ? "open" : "closed"));
        }
    }

    private final AtomicBoolean _isOpen = new AtomicBoolean(false);
    private static final Logger LOGGER = LoggerFactory.getLogger(NoHostRepository.class);
}
