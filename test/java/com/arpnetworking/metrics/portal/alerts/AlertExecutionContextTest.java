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

package com.arpnetworking.metrics.portal.alerts;

import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.alerts.scheduling.AlertExecutionContext;
import com.arpnetworking.metrics.portal.query.QueryExecutionException;
import com.arpnetworking.metrics.portal.query.QueryExecutor;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.NeverSchedule;
import com.google.common.collect.ImmutableList;
import models.internal.MetricsQueryFormat;
import models.internal.MetricsQueryResult;
import models.internal.Organization;
import models.internal.TimeSeriesResult;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.impl.DefaultAlert;
import models.internal.impl.DefaultMetricsQuery;
import models.internal.impl.DefaultMetricsQueryResult;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;

/**
 * Unit tests for {@link AlertExecutionContext}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class AlertExecutionContextTest {
    private AlertExecutionContext _context;
    private Alert _alert;
    private Schedule _schedule;
    private QueryExecutor _executor;

    @Before
    public void setUp() {
        final Organization organization = TestBeanFactory.createOrganization();
        final UUID id = UUID.randomUUID();
        _schedule = NeverSchedule.getInstance();
        _alert = new DefaultAlert.Builder()
                .setId(id)
                .setOrganization(organization)
                .setEnabled(true)
                .setName("TestAlert")
                .setDescription("Used in a test.")
                .setQuery(
                    new DefaultMetricsQuery.Builder()
                            .setQuery("This query is invalid but never evaluated")
                            .setFormat(MetricsQueryFormat.KAIROS_DB)
                            .build()
                )
                .build();
        _executor = Mockito.mock(QueryExecutor.class);
        _context = new AlertExecutionContext(
                _schedule,
                _executor
        );
    }

    @Test
    public void testReturnsTheConfiguredSchedule() {
        assertThat(_context.getSchedule(_alert), equalTo(_schedule));
    }

    @Test
    public void testQueryExecuteError() throws QueryExecutionException, InterruptedException {
        final Throwable queryError = new RuntimeException("Something went wrong");
        final CompletableFuture<MetricsQueryResult> exceptionalCompletionStage = new CompletableFuture<>();
        exceptionalCompletionStage.completeExceptionally(queryError);
        Mockito.when(_executor.executeQuery(any())).thenReturn(exceptionalCompletionStage);

        final CompletionStage<AlertEvaluationResult> cs = _context.execute(_alert, Instant.now());

        try {
            cs.toCompletableFuture().get();
            fail("Expected an exception");
        } catch (final ExecutionException e) {
            assertThat(e.getCause(), equalTo(queryError));
        }
    }

    @Test
    public void testSingleSeriesNotFiring() throws Exception {
//        Mockito.when(_executor.executeQuery(any())).thenReturn(CompletableFuture.completedFuture(
//        ));
    }

    @Test
    public void testSingleSeriesFiring() throws Exception {

    }

    @Test
    public void testGroupByMultipleFiring() throws Exception {

    }

    @Test
    public void testGroupByNoneFiring() throws Exception {

    }
}
