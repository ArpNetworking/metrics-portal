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
import models.internal.impl.DefaultMetricsQuery;
import models.internal.impl.DefaultOrganization;
import models.internal.impl.DefaultQueryResult;
import net.sf.oval.constraint.NotEmpty;
import net.sf.oval.constraint.NotNegative;
import net.sf.oval.constraint.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * An alert repository that reads definitions from the filesystem.
 *
 * @author Christian Briones (cbriones at dropbox dot com).
 */
public class FileSystemAlertRepository implements AlertRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemAlertRepository.class);
    private final AtomicBoolean _isOpen = new AtomicBoolean(false);
    private final Path _path;
    private final ObjectMapper _objectMapper;
    private final Organization _organization;
    private ImmutableMap<UUID, Alert> _alerts = ImmutableMap.of();

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
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening FileSystemAlertRepository").log();
        _alerts = loadAlerts();
        _isOpen.set(true);
    }

    @Override
    public void close() {
        assertIsOpen();
        LOGGER.debug().setMessage("Closing FileSystemAlertRepository").log();
        _isOpen.set(false);
    }

    @Override
    public Optional<Alert> getAlert(final UUID identifier, final Organization organization) {
        return Optional.ofNullable(_alerts.get(identifier));
    }

    @Override
    public AlertQuery createAlertQuery(final Organization organization) {
        return new DefaultAlertQuery(this, organization);
    }

    @Override
    public QueryResult<Alert> queryAlerts(final AlertQuery query) {
        final Predicate<Alert> containsPredicate =
                query.getContains().map(c -> (Predicate<Alert>) a -> a.getDescription().contains(c))
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
        return _alerts.size();
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
            final UUID uuid = fsAlert.getUUID().orElseGet(() -> computeUUID(fsAlert));

            // TODO(cbriones):
            // These start and end times should correspond to the interval of the
            // metric and should not be hardcoded like an ordinary query.
            final models.internal.MetricsQuery query = new DefaultMetricsQuery.Builder()
                    .setQuery(fsAlert.getQuery())
                    .setStart(ZonedDateTime.now())
                    .setEnd(ZonedDateTime.now())
                    .build();
            final Alert alert =
                    new DefaultAlert.Builder()
                            .setId(uuid)
                            .setName(fsAlert.getName())
                            .setDescription(fsAlert.getDescription())
                            .setEnabled(fsAlert.isEnabled())
                            .setOrganization(_organization)
                            .setQuery(query)
                            .setAdditionalMetadata(fsAlert.getAdditionalMetadata())
                            .build();
            mapBuilder.put(uuid, alert);
        }
        return mapBuilder.build();
    }

    private UUID computeUUID(final SerializedAlert alert) {
        // TODO(cbriones): UUID v5 rather than v3.
        //
        // The standard method returns a v3 identifier using MD5.
        // v5 uses SHA-1.

        final String alertContents =
                alert.getName() + alert.getDescription();
        return UUID.nameUUIDFromBytes(alertContents.getBytes());
    }

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_isOpen.get() != expectedState) {
            throw new IllegalStateException(String.format("FileSystemAlertRepository is not %s",
                    expectedState ? "open" : "closed"));
        }
    }

    private static final class AlertGroup {
        private final List<SerializedAlert> _alerts;
        private final long _version;

        private AlertGroup(final Builder builder) {
            _alerts = builder._alerts;
            _version = builder._version;
        }

        public long getVersion() {
            return _version;
        }

        public List<SerializedAlert> getAlerts() {
            return _alerts;
        }

        private static final class Builder extends OvalBuilder<AlertGroup> {
            private List<SerializedAlert> _alerts;
            @NotNull
            @NotNegative
            private Long _version;

            /**
             * Default constructor.
             * <p>
             * Invoked by Jackson.
             */
            Builder() {
                super(AlertGroup::new);
                _alerts = ImmutableList.of();
            }

            public Builder setAlerts(final List<SerializedAlert> alerts) {
                _alerts = alerts;
                return this;
            }

            public Builder setVersion(final long version) {
                _version = version;
                return this;
            }
        }
    }

    private static final class SerializedAlert {
        private final String _name;
        private final String _description;
        private final String _query;
        private final boolean _enabled;
        private final ImmutableMap<String, Object> _additionalMetadata;
        private final Optional<UUID> _uuid;

        private SerializedAlert(final Builder builder) {
            assert builder._enabled != null;
            assert builder._description != null;
            assert builder._query != null;

            _uuid = Optional.ofNullable(builder._uuid);
            _name = builder._name;
            _description = builder._description;
            _query = builder._query;
            _enabled = builder._enabled;
            _additionalMetadata = builder._additionalMetadata;
        }

        public Optional<UUID> getUUID() {
            return _uuid;
        }

        public String getName() {
            return _name;
        }

        public String getDescription() {
            return _description;
        }

        public String getQuery() {
            return _query;
        }

        public boolean isEnabled() {
            return _enabled;
        }

        public ImmutableMap<String, Object> getAdditionalMetadata() {
            return _additionalMetadata;
        }

        private static final class Builder extends OvalBuilder<SerializedAlert> {
            @Nullable
            private UUID _uuid;

            @NotNull
            @NotEmpty
            private String _name;

            @NotNull
            @NotEmpty
            private String _description;

            @NotNull
            @NotEmpty
            private String _query;

            @NotNull
            @Nullable
            private Boolean _enabled;

            private ImmutableMap<String, Object> _additionalMetadata = ImmutableMap.of();

            Builder() {
                super(SerializedAlert::new);
            }

            /**
             * Sets the uuid.
             *
             * @param uuid the uuid.
             * @return This instance of {@code Builder} for chaining.
             */
            public Builder setUuid(@Nullable final UUID uuid) {
                _uuid = uuid;
                return this;
            }

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
            public Builder setQuery(final String query) {
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
        }

    }
}
