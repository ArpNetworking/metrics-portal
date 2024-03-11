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
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.metrics.portal.config.ConfigProvider;
import com.arpnetworking.metrics.portal.scheduling.JobCoordinator;
import com.arpnetworking.play.configuration.ConfigurationHelper;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.StringArgGenerator;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.assistedinject.Assisted;
import com.typesafe.config.Config;
import edu.umd.cs.findbugs.annotations.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import models.internal.AlertQuery;
import models.internal.MetricsQuery;
import models.internal.MetricsQueryFormat;
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
import org.apache.pekko.actor.ActorRef;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * An alert repository that loads definitions from a pluggable configuration source.
 *
 * @author Christian Briones (cbriones at dropbox dot com).
 * @apiNote
 * This repository is read-only, so any additions, deletions, or updates will
 * result in an {@link UnsupportedOperationException}.
 * @implNote
 * This repository is currently tied to the organization given at construction, returning
 * an empty result for any other organization passed in.
 */
public class PluggableAlertRepository implements AlertRepository {
    private static final String RELOAD_SUCCESS_COUNTER = "alerts/pluggable_repository/reload/success";
    private static final String RELOAD_GAUGE = "alerts/pluggable_repository/reload/alerts";

    private static final Logger LOGGER = LoggerFactory.getLogger(PluggableAlertRepository.class);
    private static final int BUFFER_SIZE = 4096;
    private static final int LATEST_SERIALIZATION_VERSION = 0;
    private final AtomicBoolean _isOpen = new AtomicBoolean(false);
    private final ConfigProvider _configProvider;
    private final ObjectMapper _objectMapper;
    private final Organization _organization;
    private final PeriodicMetrics _periodicMetrics;
    private final ActorRef _alertJobCoordinator;
    private final Duration _openTimeout;
    private ImmutableMap<UUID, Alert> _alerts = ImmutableMap.of();

    /**
     * Injection-assisted constructor.
     *
     * This binds the configuration to the ordinary constructor.
     *
     * @param objectMapper The object mapper to use for alert deserialization.
     * @param periodicMetrics A metrics instance to record against.
     * @param alertJobCoordinator A reference to the alert job coordinator.
     * @param config The application configuration.
     */
    @Inject
    public PluggableAlertRepository(
            final ObjectMapper objectMapper,
            final PeriodicMetrics periodicMetrics,
            @Named("AlertJobCoordinator")
            final ActorRef alertJobCoordinator,
            @Assisted final Config config
    ) {
        this(
                objectMapper,
                periodicMetrics,
                ConfigurationHelper.toInstanceMapped(ConfigProvider.class, objectMapper, config.getConfig("configProvider")),
                UUID.fromString(config.getString("organization")),
                config.getDuration("openTimeout"),
                alertJobCoordinator
        );
    }

    /**
     * Constructor.
     *
     * @param objectMapper The object mapper to use for alert deserialization.
     * @param periodicMetrics A metrics instance to record against.
     * @param configProvider The config loader for the alert definitions.
     * @param org The organization to group the alerts under.
     * @param openTimeout The timeout to use when waiting for the first update at open.
     * @param alertJobCoordinator A reference to the alert job coordinator.
     */
    public PluggableAlertRepository(
            final ObjectMapper objectMapper,
            final PeriodicMetrics periodicMetrics,
            final ConfigProvider configProvider,
            final UUID org,
            final Duration openTimeout,
            final ActorRef alertJobCoordinator
    ) {
        _objectMapper = objectMapper;
        _configProvider = configProvider;
        _organization = new DefaultOrganization.Builder().setId(org).build();
        _periodicMetrics = periodicMetrics;
        _openTimeout = openTimeout;
        _alertJobCoordinator = alertJobCoordinator;
    }

    @Override
    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening PluggableAlertRepository").log();
        final CompletableFuture<Void> initialReload = new CompletableFuture<>();
        // We wrap the subscriber with two operations:
        // 1. A hook to guarantee the repository has loaded before we mark it as open.
        // 2. A hook to run anti-entropy after every reload.
        _configProvider.start(stream -> {
            reload(stream);
            if (!initialReload.isDone()) {
                initialReload.complete(null);
            }
            // Immediately kick the coordinator to pick up any job changes so that we
            // don't have to wait for anti-entropy to begin.
            //
            // NOTE: Since nodes have their own copy of this repository, this will
            // effectively kick the coordinator N times on every reload, where N is
            // the number of nodes in the cluster.
            JobCoordinator.runAntiEntropy(_alertJobCoordinator, Duration.ofSeconds(5));
        });
        try {
            initialReload.get(_openTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException("failed waiting for initial reload", e);
        }
        _isOpen.set(true);
    }

    @Override
    public void close() {
        assertIsOpen();
        LOGGER.debug().setMessage("Closing PluggableAlertRepository").log();
        _configProvider.stop();
        _isOpen.set(false);
    }

    @Override
    public Optional<Alert> getAlert(final UUID identifier, final Organization organization) {
        assertIsOpen();
        if (!_organization.equals(organization)) {
            return Optional.empty();
        }
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

        if (!query.getOrganization().equals(_organization)) {
            return new DefaultQueryResult<>(ImmutableList.of(), 0);
        }

        final Predicate<Alert> containsPredicate =
                query.getContains()
                        .map(c -> (Predicate<Alert>) a -> a.getDescription().contains(c))
                        .orElse(e -> true);

        final ImmutableList<Alert> alerts = _alerts.values().stream()
                .filter(containsPredicate)
                .filter(alert -> query.getEnabled().isEmpty() || alert.isEnabled() == query.getEnabled().get())
                .skip(query.getOffset().orElse(0))
                .limit(query.getLimit())
                .collect(ImmutableList.toImmutableList());

        final long total = _alerts.values().stream()
                .filter(containsPredicate)
                .count();

        return new DefaultQueryResult<>(alerts, total);
    }

    @Override
    public long getAlertCount(final Organization organization) {
        assertIsOpen();
        if (!_organization.equals(organization)) {
            return 0;
        }
        return _alerts.size();
    }

    /* Unsupported mutation operations */

    @Override
    public int deleteAlert(final UUID identifier, final Organization organization) {
        // Since we expect to use this repository just as every other AlertRepository,
        // we should enforce the open-before-use invariant rather than immediately
        // throwing on mutations.
        assertIsOpen();
        throw new UnsupportedOperationException("PluggableAlertRepository is read-only");
    }

    @Override
    public void addOrUpdateAlert(final Alert alert, final Organization organization) {
        assertIsOpen();
        throw new UnsupportedOperationException("PluggableAlertRepository is read-only");
    }

    private void reload(final InputStream stream) {
        LOGGER.debug().setMessage("Received update, reloading alerts").log();
        final BufferedInputStream bufferedStream = new BufferedInputStream(
                stream,
                BUFFER_SIZE
        );
        final AlertGroup group;
        try {
            group = _objectMapper.readValue(bufferedStream, AlertGroup.class);
        } catch (final IOException e) {
            LOGGER.error()
                .setMessage("Could not load alert definitions")
                .setThrowable(e)
                .log();
            _periodicMetrics.recordCounter(RELOAD_SUCCESS_COUNTER, 0);
            return;
        }
        final ImmutableMap.Builder<UUID, Alert> mapBuilder = ImmutableMap.builder();
        for (final SerializedAlert fsAlert : group.getAlerts()) {

            final StringArgGenerator uuidGen = Generators.nameBasedGenerator(_organization.getId());
            final UUID uuid = fsAlert.getUUID().orElseGet(() -> computeUUID(uuidGen, fsAlert));

            // Version-specific attributes.
            //
            // Version 0
            //    query - Queries are KairosDB JSON requests.

            if (group.getVersion() != LATEST_SERIALIZATION_VERSION) {
                final Throwable e = new IllegalArgumentException(String.format("Unhandled alert version %d", group.getVersion()));
                LOGGER.error()
                        .setMessage("Could not load alert definitions")
                        .setThrowable(e)
                        .log();
                _periodicMetrics.recordCounter(RELOAD_SUCCESS_COUNTER, 0);
                return;
            }
            final MetricsQuery query = new DefaultMetricsQuery.Builder()
                    .setQuery(fsAlert.getQuery())
                    .setFormat(MetricsQueryFormat.KAIROS_DB)
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
        _alerts = mapBuilder.build();
        _periodicMetrics.recordCounter(RELOAD_SUCCESS_COUNTER, 1);
        _periodicMetrics.recordGauge(RELOAD_GAUGE, _alerts.size());

        LOGGER.debug().setMessage("Alerts successfully reloaded")
                .addData("alertCount", _alerts.size())
                .log();
    }

    private UUID computeUUID(final StringArgGenerator uuidGen, final SerializedAlert alert) {
        final String alertContents = alert.getName();
        return uuidGen.generate(alertContents.getBytes(Charset.defaultCharset()));
    }

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_isOpen.get() != expectedState) {
            throw new IllegalStateException(String.format("PluggableAlertRepository is not %s",
                    expectedState ? "open" : "closed"));
        }
    }

    /**
     * The serialized form of alerts as expected by this repository.
     */
    public static final class AlertGroup {
        private final List<SerializedAlert> _alerts;
        private final long _version;

        private AlertGroup(final Builder builder) {
            _alerts = builder._alerts;
            _version = builder._version;
        }

        /**
         * Create an alert group from a collection of internal alerts.
         *
         * @param alerts the alerts
         * @return an alert group.
         */
        public static AlertGroup fromInternal(final ImmutableCollection<Alert> alerts) {
            final ImmutableList.Builder<SerializedAlert> serializedAlerts =
                 new ImmutableList.Builder<>();

            for (final Alert alert : alerts) {
                final SerializedAlert serialized =
                    new SerializedAlert.Builder()
                        .setName(alert.getName())
                        .setAdditionalMetadata(alert.getAdditionalMetadata())
                        .setUuid(alert.getId())
                        .setDescription(alert.getDescription())
                        .setEnabled(alert.isEnabled())
                        .setQuery(alert.getQuery().getQuery())
                        .build();
                serializedAlerts.add(serialized);
            }
            return new AlertGroup.Builder()
                    .setAlerts(serializedAlerts.build())
                    .setVersion(LATEST_SERIALIZATION_VERSION)
                    .build();
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
            private Long _version = 0L;

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

    /**
     * The alert data model for this repository.
     */
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
            private Boolean _enabled = false;

            private ImmutableMap<String, Object> _additionalMetadata = ImmutableMap.of();

            Builder() {
                super(SerializedAlert::new);
            }

            /**
             * Sets the uuid. If not present, one will be computed using the contents of the alert.
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
