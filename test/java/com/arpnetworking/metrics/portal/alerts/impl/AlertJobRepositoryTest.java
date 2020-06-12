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

import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.metrics.portal.alerts.scheduling.AlertExecutionContext;
import com.arpnetworking.metrics.portal.alerts.scheduling.AlertJobRepository;
import com.arpnetworking.metrics.portal.query.QueryExecutor;
import com.arpnetworking.metrics.portal.scheduling.Schedule;
import com.arpnetworking.metrics.portal.scheduling.impl.NeverSchedule;
import com.google.common.collect.ImmutableList;
import models.internal.AlertQuery;
import models.internal.MetricsQueryFormat;
import models.internal.Organization;
import models.internal.QueryResult;
import models.internal.alerts.Alert;
import models.internal.alerts.AlertEvaluationResult;
import models.internal.impl.DefaultAlert;
import models.internal.impl.DefaultAlertQuery;
import models.internal.impl.DefaultMetricsQuery;
import models.internal.impl.DefaultQueryResult;
import models.internal.scheduling.Job;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Unit tests for {@link AlertJobRepository}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class AlertJobRepositoryTest {
    @Mock
    private AlertRepository _alertRepository;
    private UUID _id;
    private Organization _organization;
    private Alert _alert;

    private AlertJobRepository _jobRepository;
    private AlertExecutionContext _context;

    @Before
    public void setUp() {
        final QueryExecutor mockExecutor = Mockito.mock(QueryExecutor.class);

        _organization = TestBeanFactory.createOrganization();
        _id = UUID.randomUUID();
        _alert = new DefaultAlert.Builder()
                .setId(_id)
                .setOrganization(_organization)
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

        // Create a mock AlertRepository that contains a single alert.
        MockitoAnnotations.initMocks(this);
        Mockito.when(_alertRepository.getAlert(_id, _organization))
                .thenReturn(Optional.of(_alert));

        final Schedule schedule = NeverSchedule.getInstance();
        _context = new AlertExecutionContext(schedule, mockExecutor);
        _jobRepository = new AlertJobRepository(_alertRepository, _context);
    }

    @Test
    public void testGetJobWrapsAlert() {
        final Optional<Job<AlertEvaluationResult>> mjob = _jobRepository.getJob(_id, _organization);
        if (!mjob.isPresent()) {
            fail("Job not found: " + _id);
        }
        final Job<AlertEvaluationResult> job = mjob.get();
        assertThat(job.getId(), equalTo(_id));

        final Schedule alertSchedule = _context.getSchedule(_alert);
        assertThat(job.getSchedule(), equalTo(alertSchedule));
    }

    @Test
    public void testJobQueryProxiesParametersAndResults() {
        // Arbitrary since we're just testing proxying logic
        final long alertTotal = 42;
        final int offset = 7;
        final int limit = 100;

        // mock AlertRepository such that for the above query params it returns
        // a page with a single alert.
        final ArgumentCaptor<AlertQuery> captor = ArgumentCaptor.forClass(AlertQuery.class);
        Mockito.when(_alertRepository.createAlertQuery(eq(_organization))).thenReturn(new DefaultAlertQuery(
                _alertRepository,
                _organization));
        Mockito.when(_alertRepository.queryAlerts(any())).thenReturn(new DefaultQueryResult<>(ImmutableList.of(_alert),
                alertTotal));

        final QueryResult<Job<AlertEvaluationResult>> wrappedResults = _jobRepository
                .createJobQuery(_organization)
                .offset(offset)
                .limit(limit)
                .execute();
        Mockito.verify(_alertRepository).queryAlerts(captor.capture());
        final AlertQuery proxiedQuery = captor.getValue();

        assertThat(proxiedQuery.getOffset(), equalTo(Optional.of(offset)));
        assertThat(proxiedQuery.getLimit(), equalTo(limit));
        assertThat(proxiedQuery.getOrganization(), equalTo(_organization));
        assertThat(wrappedResults.total(), equalTo(alertTotal));
        assertThat(wrappedResults.values(), hasSize(1));
        assertThat(wrappedResults.values().get(0).getId(), equalTo(_id));
    }
}
