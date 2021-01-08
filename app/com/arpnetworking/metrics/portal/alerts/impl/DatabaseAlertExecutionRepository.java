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
import akka.pattern.Patterns;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.alerts.AlertExecutionRepository;
import com.arpnetworking.metrics.portal.scheduling.JobExecutionRepository;
import com.arpnetworking.metrics.portal.scheduling.impl.DatabaseExecutionHelper;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.ebean.EbeanServer;
import io.ebean.RawSql;
import io.ebean.RawSqlBuilder;
import models.ebean.AlertExecution;
import models.internal.Organization;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.scheduling.JobExecution;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    private static final Duration ACTOR_STOP_TIMEOUT = Duration.ofSeconds(5);

    private final AtomicBoolean _isOpen = new AtomicBoolean(false);
    private final EbeanServer _ebeanServer;
    private final DatabaseExecutionHelper<AlertEvaluationResult, AlertExecution> _helper;

    private static final String ACTOR_NAME = "alertExecutionPartitionCreator";
    private final Executor _executor;
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
     * @param executor The executor to use for the DB operations
     */
    @Inject
    public DatabaseAlertExecutionRepository(
            final EbeanServer portalServer,
            final EbeanServer partitionServer,
            final ActorSystem actorSystem,
            final PeriodicMetrics periodicMetrics,
            final Duration partitionCreationOffset,
            final int partitionCreationLookahead,
            final Executor executor
    ) {
        _ebeanServer = portalServer;
        _helper = new DatabaseExecutionHelper<>(LOGGER, _ebeanServer, this::findOrCreateAlertExecution, executor);
        _actorSystem = actorSystem;
        _executor = executor;
        _props = DailyPartitionCreator.props(
                partitionServer,
                periodicMetrics,
                "portal",
                "alert_executions",
                partitionCreationOffset,
                partitionCreationLookahead
        );
    }

    private CompletionStage<AlertExecution> findOrCreateAlertExecution(
            final UUID jobId,
            final Organization organization,
            final Instant scheduled
    ) {
        return CompletableFuture.supplyAsync(() -> {
            final Optional<models.ebean.Organization> org = models.ebean.Organization.findByOrganization(_ebeanServer,
                    organization);
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
                            .eq("alert_id", jobId)
                            .findOneOrEmpty()
            );
            final AlertExecution newOrUpdatedExecution = existingExecution.orElseGet(AlertExecution::new);
            newOrUpdatedExecution.setAlertId(jobId);
            newOrUpdatedExecution.setOrganization(org.get());
            newOrUpdatedExecution.setScheduled(scheduled);
            return newOrUpdatedExecution;
        }, _executor);
    }

    @Override
    public void open() {
        assertIsOpen(false);
        LOGGER.debug().setMessage("Opening DatabaseAlertExecutionRepository").log();
        _partitionCreator = _actorSystem.actorOf(_props);
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
            Patterns.gracefulStop(_partitionCreator, ACTOR_STOP_TIMEOUT)
                    .toCompletableFuture()
                    .get(ACTOR_STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            _partitionCreator = null;
        } catch (final TimeoutException | ExecutionException | InterruptedException e) {
            throw new RuntimeException("Failed to shutdown partition creator", e);
        }
        _isOpen.set(false);
    }

    @Override
    public CompletionStage<Optional<JobExecution<AlertEvaluationResult>>> getLastScheduled(
            final UUID jobId,
            final Organization organization
    ) throws NoSuchElementException {
        assertIsOpen();
        return CompletableFuture.supplyAsync(() ->
            _ebeanServer.find(AlertExecution.class)
                    .where()
                    .eq("alert_id", jobId)
                    .eq("organization.uuid", organization.getId())
                    .setMaxRows(1)
                    .orderBy()
                    .desc("scheduled")
                    .findOneOrEmpty()
                    .map(DatabaseExecutionHelper::toInternalModel),
                _executor);
    }

    @Override
    public CompletionStage<Optional<JobExecution.Success<AlertEvaluationResult>>> getLastSuccess(
            final UUID jobId,
            final Organization organization
    ) throws NoSuchElementException {
        assertIsOpen();
        return CompletableFuture.supplyAsync(() -> {
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
        }, _executor);
    }

    @Override
    public CompletionStage<ImmutableMap<UUID, JobExecution.Success<AlertEvaluationResult>>> getLastSuccessBatch(
            final List<UUID> jobIds,
            final Organization organization,
            final LocalDate maxLookback
    ) throws NoSuchElementException {
        assertIsOpen();

        // Ebean doesn't play well with nested queries or aggregate functions, let
        // alone both. As such we opt for manually specifying the query here.
        //
        // This query performs fairly well when the number of indices is bounded,
        // otherwise Postgres will attempt to scan all of them individually.
        //
        // 4 Nov 2020
        // Using EXPLAIN ANALYZE, 1000 job IDs and 1 day lookback.
        //
        // Planning time: 64.001 ms
        // Execution time: 135.614 ms

        final String query =
                  " SELECT t1.organization_id, t1.alert_id, t1.scheduled, t1.started_at, t1.completed_at, t1.state, t1.result"
                + " FROM portal.alert_executions t1"
                + " JOIN (SELECT alert_id, max(completed_at) completed_at"
                + "         FROM portal.alert_executions"
                + "         WHERE scheduled >= :scheduled"
                + "           AND state = :state"
                + "         GROUP BY alert_id) t2"
                + " ON t1.alert_id = t2.alert_id AND t1.completed_at = t2.completed_at"
                + " WHERE t1.organization_id = (SELECT id FROM portal.organizations WHERE uuid = :organization_uuid)";

        final RawSql rawSql = RawSqlBuilder
                .parse(query)
                .columnMapping("t1.organization_id", "organization.id")
                .columnMapping("t1.alert_id", "alertId")
                .columnMapping("t1.scheduled", "scheduled")
                .columnMapping("t1.started_at", "started_at")
                .columnMapping("t1.completed_at", "completed_at")
                .columnMapping("t1.state", "state")
                .columnMapping("t1.result", "result")
                .create();


        return CompletableFuture.supplyAsync(() -> {
            final List<AlertExecution> rows =
                    _ebeanServer.find(AlertExecution.class)
                            .setRawSql(rawSql)
                            .setParameter("scheduled", maxLookback)
                            .setParameter("organization_uuid", organization.getId())
                            .setParameter("state", AlertExecution.State.SUCCESS)
                            .where()
                            .gt("scheduled", maxLookback)
                            .in("alertId", jobIds)
                            .findList();
            return rows
                    .stream()
                    .map(DatabaseExecutionHelper::toInternalModel)
                    .map(result -> {
                        if (result instanceof JobExecution.Success) {
                            return (JobExecution.Success<AlertEvaluationResult>) result;
                        }
                        throw new IllegalStateException(
                                String.format("execution returned was not a success when specified by the query: %s", result)
                        );
                    })
                    .collect(ImmutableMap.toImmutableMap(
                            JobExecution::getJobId,
                            execution -> execution
                    ));
        }, _executor);
    }

    @Override
    public CompletionStage<Optional<JobExecution<AlertEvaluationResult>>> getLastCompleted(
            final UUID jobId,
            final Organization organization
    ) throws NoSuchElementException {
        assertIsOpen();
        return CompletableFuture.supplyAsync(() -> _ebeanServer.find(AlertExecution.class)
                .where()
                .eq("alert_id", jobId)
                .eq("organization.uuid", organization.getId())
                .in("state", AlertExecution.State.SUCCESS, AlertExecution.State.FAILURE)
                .setMaxRows(1)
                .orderBy()
                .desc("completed_at")
                .findOneOrEmpty()
                .map(DatabaseExecutionHelper::toInternalModel),
                _executor);
    }

    @Override
    public CompletionStage<Void> jobStarted(final UUID alertId, final Organization organization, final Instant scheduled) {
        assertIsOpen();
        return ensurePartition(scheduled).thenCompose(
                ignore -> _helper.jobStarted(alertId, organization, scheduled)
        );
    }

    @Override
    public CompletionStage<JobExecution.Success<AlertEvaluationResult>> jobSucceeded(
            final UUID alertId,
            final Organization organization,
            final Instant scheduled,
            final AlertEvaluationResult result
    ) {
        assertIsOpen();
        return ensurePartition(scheduled).thenCompose(
                ignore -> _helper.jobSucceeded(alertId, organization, scheduled, result)
        )
        .thenApply(DatabaseExecutionHelper::toInternalModel)
        .thenApply(e -> {
            if (!(e instanceof JobExecution.Success)) {
                throw new IllegalStateException("not a success");
            }
            return (JobExecution.Success<AlertEvaluationResult>) e;
        });
    }

    @Override
    public CompletionStage<Void> jobFailed(
            final UUID alertId,
            final Organization organization,
            final Instant scheduled,
            final Throwable error
    ) {
        assertIsOpen();
        return ensurePartition(scheduled).thenCompose(
            ignore -> _helper.jobFailed(alertId, organization, scheduled, error)
        );
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

    private CompletionStage<Void> ensurePartition(final Instant scheduled) {
        if (_partitionCreator == null) {
            throw new IllegalStateException("partitionCreator should be non-null when open");
        }
        return DailyPartitionCreator.ensurePartitionExistsForInstant(
                _partitionCreator,
                scheduled,
                Duration.ofSeconds(1)
        );
    }
}
