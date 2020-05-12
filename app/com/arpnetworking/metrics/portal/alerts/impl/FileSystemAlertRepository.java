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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import models.internal.AlertQuery;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.alerts.Alert;
import models.internal.impl.DefaultAlertQuery;
import models.internal.impl.DefaultQueryResult;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * An alert repository that reads definitions from the filesystem.
 *
 * @author Christian Briones (cbriones at dropbox dot com).
 */
public class FileSystemAlertRepository implements AlertRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemAlertRepository.class);
    private final Path _path;
    private final ObjectMapper _objectMapper;
    private ImmutableMap<UUID, Alert> _alerts = ImmutableMap.of();
    private final RepositoryOpenGuard _openGuard =
            new RepositoryOpenGuard(
                    () -> {
                        LOGGER.debug().setMessage("Opening FileSystemAlertRepository").log();
                        _alerts = loadAlerts();
                    },
                    () -> LOGGER.debug().setMessage("Closing FileSystemAlertRepository").log()
            );

    public FileSystemAlertRepository(final ObjectMapper objectMapper, final Path path) {
        _objectMapper = objectMapper;
        _path = path;
    }

    @Override
    public void open() {
        _openGuard.open();
    }

    @Override
    public void close() {
        _openGuard.close();
    }

    @Override
    public Optional<Alert> getAlert(final UUID identifier, final Organization organization) {
        return _openGuard.checked(() -> Optional.ofNullable(_alerts.get(identifier)));
    }

    @Override
    public AlertQuery createAlertQuery(final Organization organization) {
        return _openGuard.checked(() -> new DefaultAlertQuery(this, organization));
    }

    @Override
    public QueryResult<Alert> queryAlerts(final AlertQuery query) {
        final Predicate<Alert> containsPredicate =
                query.getContains().map(c -> (Predicate<Alert>) a -> a.getDescription().contains(c))
                        .orElse(e -> true);

        return _openGuard.checked(() -> {
            final ImmutableList<Alert> alerts = _alerts.values().stream()
                    .filter(containsPredicate)
                    .skip(query.getOffset().orElse(0))
                    .limit(query.getOffset().orElse(1000))
                    .collect(ImmutableList.toImmutableList());

            // TODO: is this total like the entire set or total like how big the page is?
            return new DefaultQueryResult<>(alerts, alerts.size());
        });
    }

    @Override
    public long getAlertCount(final Organization organization) {
        return _openGuard.checked(() -> _alerts.size());
    }

    /* Unsupported mutation operations */

    @Override
    public int deleteAlert(final UUID identifier, final Organization organization) {
        throw new UnsupportedOperationException("FilesystemAlertRepository is read-only");
    }

    @Override
    public void addOrUpdateAlert(final Alert alert, final Organization organization) {
        throw new UnsupportedOperationException("FilesystemAlertRepository is read-only");
    }

    private ImmutableMap<UUID, Alert> loadAlerts() {
        final AlertGroup group;
        try (Reader reader = Files.newBufferedReader(_path)) {
            group = _objectMapper.readValue(
                    reader,
                    AlertGroup.class);
        } catch (final IOException e) {
            throw new RuntimeException("Could not load alerts from filesystem", e);
        }
        final ImmutableMap.Builder<UUID, Alert> mapBuilder = ImmutableMap.builder();
        for (final Alert alert : group.getAlerts()) {
            final UUID uuid = computeUUID(alert);
            mapBuilder.put(uuid, alert);
        }
        return mapBuilder.build();
    }

    private UUID computeUUID(final Alert alert) {
        // TODO(cbriones): UUID v5
        return UUID.randomUUID();
    }

    private static final class AlertGroup {
        private final UUID _organizationId;
        private final List<Alert> _alerts;
        private final long _version;

        private AlertGroup(final UUID organizationId, final List<Alert> alerts, final long version) {
            _alerts = alerts;
            _version = version;
            _organizationId = organizationId;
        }

        public long getVersion() {
            return _version;
        }

        public List<Alert> getAlerts() {
            return _alerts;
        }
    }

    /**
     * Helper class for managing the open/closed state of a repository.
     * <p>
     * The open and close methods on this class are <b>not</b> idempotent, and will throw if performed more than once.
     */
    private static final class RepositoryOpenGuard {
        private static final Runnable NOP_RUNNABLE = () -> {
        };

        private final AtomicBoolean _isOpen = new AtomicBoolean(false);
        private final Runnable _onOpen;
        private final Runnable _onClose;

        /**
         * Create a new guard without callbacks.
         */
        RepositoryOpenGuard() {
            this(NOP_RUNNABLE, NOP_RUNNABLE);
        }

        /**
         * Create a guard with the given callbacks.
         *
         * @param onOpen the operation to run before opening.
         * @param onClose the operation to run before closing.
         */
        RepositoryOpenGuard(final Runnable onOpen, final Runnable onClose) {
            _onOpen = onOpen;
            _onClose = onClose;
        }

        /**
         * Mark the guard as open.
         *
         * @throws IllegalStateException if the guard is already open.
         */
        public void open() {
            assertIsOpen(false);
            _onOpen.run();
            _isOpen.set(true);
        }

        /**
         * Mark the guard as closed.
         *
         * @throws IllegalStateException if the guard is already closed.
         */
        public void close() {
            assertIsOpen();
            _onClose.run();
            _isOpen.set(false);
        }

        /**
         * Execute an action iff the guard is open.
         *
         * @param action the action to run if open.
         * @throws IllegalStateException if the guard is closed.
         */
        public void checked(final Runnable action) {
            checked(() -> {
                action.run();
                return null;
            });
        }

        /**
         * Execute an action iff the guard is open. The result of the action is returned to the caller.
         *
         * @param action the action to run if open.
         * @throws IllegalStateException if the guard is closed.
         */
        public <T> T checked(final Supplier<T> action) {
            assertIsOpen();
            return action.get();
        }

        private void assertIsOpen() {
            assertIsOpen(true);
        }

        private void assertIsOpen(final boolean expectedState) {
            if (_isOpen.get() != expectedState) {
                throw new IllegalStateException(String.format("RepositoryOpenGuard is not %s",
                        expectedState ? "open" : "closed"));
            }
        }
    }
}
