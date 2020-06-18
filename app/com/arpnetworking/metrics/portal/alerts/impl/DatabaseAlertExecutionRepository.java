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

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.alerts.AlertExecutionRepository;
import com.arpnetworking.metrics.portal.scheduling.JobExecutionRepository;
import com.arpnetworking.metrics.portal.scheduling.impl.DatabaseExecutionHelper;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.ebean.EbeanServer;
import models.ebean.AlertExecution;
import models.internal.Organization;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.scheduling.JobExecution;

import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.persistence.EntityNotFoundException;

/**
 * Implementation of {@link JobExecutionRepository} for {@link Alert} jobs using a SQL database.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class DatabaseAlertExecutionRepository implements AlertExecutionRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseAlertExecutionRepository.class);

    private final AtomicBoolean _isOpen = new AtomicBoolean(false);
    private final EbeanServer _ebeanServer;
    private final DatabaseExecutionHelper<AlertEvaluationResult, AlertExecution> _helper;

    private static final String ACTOR_NAME = "alertExecutionPartitionCreator";
    @Nullable
    private ActorRef _partitionCreator;
    private final Props _props;
    private final ActorSystem _actorSystem;

    /**
     * Public constructor.
     *
     * @param portalServer Play's {@code EbeanServer} for this repository.
     * @param partitionServer Play's {@code EbeanServer} for partition creation.
     * @param actorSystem The actor system to use.
     * @param periodicMetrics A metrics instance to record against.
     * @param partitionCreationOffset Daily offset for partition creation, e.g. 0 is midnight
     * @param partitionCreationLookahead How many days of partitions to create
     */
    @Inject
    public DatabaseAlertExecutionRepository(
            final EbeanServer portalServer,
            final EbeanServer partitionServer,
            final ActorSystem actorSystem,
            final PeriodicMetrics periodicMetrics,
            final Duration partitionCreationOffset,
            final int partitionCreationLookahead
    ) {
        _ebeanServer = portalServer;
        _helper = new DatabaseExecutionHelper<>(LOGGER, _ebeanServer, this::findOrCreateAlertExecution);
        _actorSystem = actorSystem;
        _props = DailyPartitionCreator.props(
                partitionServer,
                periodicMetrics,
                "portal",
                "alert_executions",
                partitionCreationOffset,
                partitionCreationLookahead
        );
    }

    private AlertExecution findOrCreateAlertExecution(
            final UUID jobId,
            final Organization organization,
            final Instant scheduled
    ) {
        final Optional<models.ebean.Organization> org = models.ebean.Organization.findByOrganization(_ebeanServer, organization);
        if (!org.isPresent()) {
            final String message = String.format(
                    "Could not find org with organization.uuid=%s",
                    organization.getId()
            );
            throw new EntityNotFoundException(message);
        }
        final Optional<AlertExecution> existingExecution = org.flatMap(r ->
                _ebeanServer.createQuery(AlertExecution.class)
                        .where()
                        .eq("organization.uuid", org.get().getUuid())
                        .eq("scheduled", scheduled)
                        .findOneOrEmpty()
        );
        final AlertExecution newOrUpdatedExecution = existingExecution.orElseGet(AlertExecution::new);
        newOrUpdatedExecution.setAlertId(jobId);
        newOrUpdatedExecution.setOrganization(org.get());
        newOrUpdatedExecution.setScheduled(scheduled);
        return newOrUpdatedExecution;
    }

    @Override
    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening DatabaseAlertExecutionRepository").log();
        try {
            _partitionCreator = _actorSystem.actorOf(_props);
            DailyPartitionCreator.start(_partitionCreator, Duration.ofSeconds(5));
        } catch (final ExecutionException | InterruptedException e) {
            throw new RuntimeException("Failed to start partition creator", e);
        }
        _isOpen.set(true);
    }

    @Override
    public void close() {
        assertIsOpen();
        LOGGER.debug().setMessage("Closing DatabaseAlertExecutionRepository").log();
        if (_partitionCreator == null) {
            throw new IllegalStateException("partitionCreator should be non-null when open");
        }
        try {
            DailyPartitionCreator.stop(_partitionCreator, Duration.ofSeconds(5));
            _partitionCreator = null;
        } catch (final ExecutionException | InterruptedException e) {
            throw new RuntimeException("Failed to shutdown partition creator", e);
        }
        _isOpen.set(false);
    }

    @Override
    public Optional<JobExecution<AlertEvaluationResult>> getLastScheduled(final UUID jobId, final Organization organization)
            throws NoSuchElementException {
        assertIsOpen();
        return _ebeanServer.find(AlertExecution.class)
                .where()
                .eq("alert_id", jobId)
                .eq("organization.uuid", organization.getId())
                .setMaxRows(1)
                .orderBy()
                .desc("scheduled")
                .findOneOrEmpty()
                .map(DatabaseExecutionHelper::toInternalModel);
    }

    @Override
    public Optional<JobExecution.Success<AlertEvaluationResult>> getLastSuccess(final UUID jobId, final Organization organization)
            throws NoSuchElementException {
        assertIsOpen();
        final Optional<AlertExecution> row = _ebeanServer.find(AlertExecution.class)
                .where()
                .eq("alert_id", jobId)
                .eq("organization.uuid", organization.getId())
                .eq("state", AlertExecution.State.SUCCESS)
                .setMaxRows(1)
                .orderBy()
                .desc("completed_at")
                .findOneOrEmpty();
        if (row.isPresent()) {
            final JobExecution<AlertEvaluationResult> execution = DatabaseExecutionHelper.toInternalModel(row.get());
            if (execution instanceof JobExecution.Success) {
                return Optional.of((JobExecution.Success<AlertEvaluationResult>) execution);
            }
            throw new IllegalStateException(
                    String.format("execution returned was not a success when specified by the query: %s", row.get())
            );
        }
        return Optional.empty();
    }

    @Override
    public Optional<JobExecution<AlertEvaluationResult>> getLastCompleted(final UUID jobId, final Organization organization)
            throws NoSuchElementException {
        assertIsOpen();
        return _ebeanServer.find(AlertExecution.class)
                .where()
                .eq("alert_id", jobId)
                .eq("organization.uuid", organization.getId())
                .in("state", AlertExecution.State.SUCCESS, AlertExecution.State.FAILURE)
                .setMaxRows(1)
                .orderBy()
                .desc("completed_at")
                .findOneOrEmpty()
                .map(DatabaseExecutionHelper::toInternalModel);
    }

    @Override
    public void jobStarted(final UUID alertId, final Organization organization, final Instant scheduled) {
        assertIsOpen();
        _helper.jobStarted(alertId, organization, scheduled);
    }

    @Override
    public void jobSucceeded(
            final UUID alertId,
            final Organization organization,
            final Instant scheduled,
            final AlertEvaluationResult result
    ) {
        assertIsOpen();
        _helper.jobSucceeded(alertId, organization, scheduled, result);
    }

    @Override
    public void jobFailed(final UUID alertId, final Organization organization, final Instant scheduled, final Throwable error) {
        assertIsOpen();
        _helper.jobFailed(alertId, organization, scheduled, error);
    }

    private void assertIsOpen() {
        assertIsOpen(true);
    }

    private void assertIsOpen(final boolean expectedState) {
        if (_isOpen.get() != expectedState) {
            throw new IllegalStateException(String.format("DatabaseAlertExecutionRepository is not %s",
                    expectedState ? "open" : "closed"));
        }
    }
}
