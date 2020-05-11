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

import com.arpnetworking.metrics.portal.TestBeanFactory;
import com.arpnetworking.metrics.portal.alerts.impl.DatabaseAlertExecutionRepository;
import com.arpnetworking.metrics.portal.integration.test.EbeanServerHelper;
import com.arpnetworking.metrics.portal.scheduling.JobExecutionRepository;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.ebean.EbeanServer;
import models.internal.AlertEvaluationResult;
import models.internal.Organization;
import models.internal.impl.DefaultAlertEvaluationResult;

import java.util.UUID;

/**
 * Integration tests for {@link DatabaseAlertExecutionRepository}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class DatabaseAlertExecutionRepositoryIT extends JobExecutionRepositoryIT<AlertEvaluationResult> {
    @Override
    public JobExecutionRepository<AlertEvaluationResult> setUpRepository(final Organization organization, final UUID jobId) {
        final EbeanServer server = EbeanServerHelper.getMetricsDatabase();

        final models.ebean.Organization ebeanOrganization = TestBeanFactory.createEbeanOrganization();
        ebeanOrganization.setUuid(organization.getId());
        server.save(ebeanOrganization);

        // DatabaseAlertExecutionRepository does not validate that the JobID is a valid AlertID since those
        // references are not constrained in the underlying execution table.

        return new DatabaseAlertExecutionRepository(server);
    }

    @Override
    AlertEvaluationResult newResult() {
        return new DefaultAlertEvaluationResult.Builder()
                .setFiringTags(ImmutableList.of(ImmutableMap.of("tag-name", "tag-value")))
                .build();
    }
}
