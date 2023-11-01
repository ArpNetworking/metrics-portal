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

package com.arpnetworking.metrics.portal.integration.repositories;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.alerts.impl.DatabaseAlertExecutionRepository;
import com.arpnetworking.metrics.portal.integration.test.EbeanServerHelper;
import com.arpnetworking.metrics.portal.scheduling.JobExecutionRepository;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.ebean.EbeanServer;
import models.internal.Organization;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.impl.DefaultAlertEvaluationResult;
import net.sf.oval.exception.ConstraintsViolatedException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Integration tests for {@link DatabaseAlertExecutionRepository}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class DatabaseAlertExecutionRepositoryIT extends JobExecutionRepositoryIT<AlertEvaluationResult> {
    private ActorSystem _actorSystem;

    @Override
    public JobExecutionRepository<AlertEvaluationResult> setUpRepository(final Organization organization) {
        final EbeanServer server = EbeanServerHelper.getMetricsDatabase();
        final EbeanServer adminServer = EbeanServerHelper.getAdminMetricsDatabase();

        final models.ebean.Organization ebeanOrganization = TestBeanFactory.createEbeanOrganization();
        ebeanOrganization.setUuid(organization.getId());
        server.save(ebeanOrganization);

        _actorSystem = ActorSystem.create();
        final PeriodicMetrics metricsMock = Mockito.mock(PeriodicMetrics.class);
        final DatabaseAlertExecutionRepository.PartitionManager partitionManager =
                new DatabaseAlertExecutionRepository.PartitionManager.Builder()
                        .setOffset(scala.concurrent.duration.Duration.Zero())
                        .setLookahead(5)
                        .setRetainCount(25)
                        .build();

        return new DatabaseAlertExecutionRepository(
                server,
                adminServer,
                _actorSystem,
                metricsMock,
                partitionManager,
                Executors.newSingleThreadExecutor()
        );
    }

    @Override
    public void ensureJobExists(final Organization organization, final UUID jobId) {
        // DatabaseAlertExecutionRepository does not validate that the JobID is a valid AlertID since those
        // references are not constrained in the underlying execution table.
        final EbeanServer server = EbeanServerHelper.getMetricsDatabase();
        final Optional<models.ebean.Organization> org = models.ebean.Organization.findByOrganization(server, organization);
        if (!org.isPresent()) {
            throw new IllegalStateException("organization not found: " + organization);
        }
    }

    @Test
    public void testPartitionManagerBuilder() {
        DatabaseAlertExecutionRepository.PartitionManager pm;
        pm = new DatabaseAlertExecutionRepository.PartitionManager.Builder()
                .build();
        Assert.assertNotNull(pm);

        pm = new DatabaseAlertExecutionRepository.PartitionManager.Builder()
                .setLookahead(7)
                .build();
        Assert.assertNotNull(pm);

        pm = new DatabaseAlertExecutionRepository.PartitionManager.Builder()
                .setLookahead(7)
                .setRetainCount(30)
                .build();
        Assert.assertNotNull(pm);

        try {
            pm = new DatabaseAlertExecutionRepository.PartitionManager.Builder()
                    .setLookahead(7)
                    .setRetainCount(3)
                    .build();
            Assert.fail("Expected exception");
        } catch (final ConstraintsViolatedException ignored) {
        }
    }

    @Override
    public void tearDown() {
        super.tearDown();
        TestKit.shutdownActorSystem(_actorSystem);
    }

    @Override
    AlertEvaluationResult newResult() {
        final Instant queryEnd = Instant.now();
        return new DefaultAlertEvaluationResult.Builder()
                .setSeriesName("example-series")
                .setFiringTags(ImmutableList.of(ImmutableMap.of("tag-name", UUID.randomUUID().toString())))
                .setGroupBys(ImmutableList.of("tag-name"))
                .setQueryStartTime(queryEnd.minus(Duration.ofMinutes(1)))
                .setQueryEndTime(queryEnd)
                .build();
    }
}
