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

import com.arpnetworking.commons.builder.OvalBuilder;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import edu.umd.cs.findbugs.annotations.Nullable;
import models.internal.AlertQuery;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.alerts.Alert;
import models.internal.impl.DefaultAlert;
import models.internal.impl.DefaultAlertQuery;
import models.internal.impl.DefaultOrganization;
import models.internal.impl.DefaultQueryResult;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
    private final Organization _organization;
    private ImmutableMap<UUID, Alert> _alerts = ImmutableMap.of();
    private final RepositoryOpenGuard _openGuard =
            new RepositoryOpenGuard(
                    () -> {
                        LOGGER.debug().setMessage("Opening FileSystemAlertRepository").log();
                        _alerts = loadAlerts();
                    },
                    () -> LOGGER.debug().setMessage("Closing FileSystemAlertRepository").log()
            );

    /**
     * Default Constructor.
     *
     * @param objectMapper The object mapper to use for alert deserialization.
     * @param path The file path for the alert definitions.
     * @param org The organization to group the alerts under.
     */
    @Inject
    public FileSystemAlertRepository(
            final ObjectMapper objectMapper,
            @Named("fileSystemAlertRepository.path") final Path path,
            @Named("fileSystemAlertRepository.org") final UUID org
    ) {
        _objectMapper = objectMapper;
        _path = path;
        _organization = new DefaultOrganization.Builder().setId(org).build();
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

        for (final SerializedAlert fsAlert : group.getAlerts()) {
            final UUID uuid = computeUUID(fsAlert);
            final Alert alert =
                new DefaultAlert.Builder()
                        .setId(uuid)
                        .setName(fsAlert.getName())
                        .setDescription(fsAlert.getDescription())
                        .setEnabled(fsAlert.isEnabled())
                        .setOrganization(_organization)
                        .setQuery(new KairosDbMetricsQuery(fsAlert.getQuery()))
                        .setAdditionalMetadata(fsAlert.getAdditionalMetadata())
                        .build();
            mapBuilder.put(uuid, alert);
        }
        return mapBuilder.build();
    }

    private UUID computeUUID(final SerializedAlert alert) {
        // TODO(cbriones): UUID v5
        return UUID.randomUUID();
    }

    private static final class KairosDbMetricsQuery implements models.internal.MetricsQuery {
        private final MetricsQuery _metricsQuery;

        private KairosDbMetricsQuery(final MetricsQuery metricsQuery) {
            _metricsQuery = metricsQuery;
        }

        @Override
        public String getQuery() {
            // TODO(cbriones): We should be able to serialize this into an equivalent
            // query once we have our query language nailed down.
            return "";
        }

        @Override
        public ZonedDateTime getStart() {
            return ZonedDateTime.now()
                    .truncatedTo(ChronoUnit.MINUTES)
                    .minus(Duration.ofMinutes(1));
        }

        @Override
        public ZonedDateTime getEnd() {
            return ZonedDateTime.now().truncatedTo(ChronoUnit.MINUTES);
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
         * Create a new guard without open/close callbacks.
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
         * @return The result of the passed in action.
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

    private static final class AlertGroup {
        private final List<SerializedAlert> _alerts;
        private final long _version;

        private AlertGroup(final List<SerializedAlert> alerts, final long version) {
            if (version < 0) {
                throw new IllegalArgumentException("Version must be non-negative");
            }
            _alerts = alerts;
            _version = version;
        }

        public long getVersion() {
            return _version;
        }

        public List<SerializedAlert> getAlerts() {
            return _alerts;
        }
    }

    private static final class SerializedAlert {
        private final String _name;
        private final String _description;
        private final com.arpnetworking.kairos.client.models.MetricsQuery _query;
        private final boolean _enabled;
        private final ImmutableMap<String, Object> _additionalMetadata;

        private SerializedAlert(final Builder builder) {
            assert builder._enabled != null;
            assert builder._description != null;
            assert builder._query != null;

            _name = builder._name;
            _description = builder._description;
            _query = builder._query;
            _enabled = builder._enabled;
            _additionalMetadata = builder._additionalMetadata;
        }

        public String getName() {
            return _name;
        }

        public String getDescription() {
            return _description;
        }

        public MetricsQuery getQuery() {
            return _query;
        }

        public boolean isEnabled() {
            return _enabled;
        }

        public ImmutableMap<String, Object> getAdditionalMetadata() {
            return _additionalMetadata;
        }

        private static final class Builder extends OvalBuilder<SerializedAlert> {
            @NotNull
            @NotEmpty
            private String _name;
            @NotNull
            private @Nullable String _description;
            @NotNull
            private @Nullable com.arpnetworking.kairos.client.models.MetricsQuery _query;
            @NotNull
            private @Nullable Boolean _enabled;

            private ImmutableMap<String, Object> _additionalMetadata = ImmutableMap.of();

            /**
             * Sets the name. Required. Cannot be empty.
             *
             * @param name the name.
             * @return This instance of {@code Builder} for chaining.
             */
            public Builder setName(final String name) {
                _name = name;
                return this;
            }

            /**
             * Sets the description. Required.
             *
             * @param description the description.
             * @return This instance of {@code Builder} for chaining.
             */
            public Builder setDescription(final String description) {
                _description = description;
                return this;
            }

            /**
             * Sets the query. Required.
             *
             * @param query the query.
             * @return This instance of {@code Builder} for chaining.
             */
            public Builder setQuery(final MetricsQuery query) {
                _query = query;
                return this;
            }

            /**
             * Sets enabled. Required.
             *
             * @param enabled if this alert is enabled.
             * @return This instance of {@code Builder} for chaining.
             */
            public Builder setEnabled(final boolean enabled) {
                _enabled = enabled;
                return this;
            }

            /**
             * Sets the additional metadata. Defaults to empty.
             *
             * @param additionalMetadata the additional metadata.
             * @return This instance of {@code Builder} for chaining.
             */
            public Builder setAdditionalMetadata(final Map<String, Object> additionalMetadata) {
                _additionalMetadata = ImmutableMap.copyOf(additionalMetadata);
                return this;
            }

            Builder() {
                super(SerializedAlert::new);
            }
        }

    }
}
