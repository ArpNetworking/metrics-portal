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
import com.arpnetworking.metrics.portal.integration.test.EbeanServerHelper;
import com.arpnetworking.metrics.portal.reports.impl.DatabaseReportExecutionRepository;
import com.arpnetworking.metrics.portal.scheduling.JobExecutionRepository;
import io.ebean.Database;
import models.internal.Organization;
import models.internal.impl.DefaultReportResult;
import models.internal.reports.Report;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Integration tests for {@link DatabaseReportExecutionRepository}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class DatabaseReportExecutionRepositoryIT extends JobExecutionRepositoryIT<Report.Result> {
    @Override
    JobExecutionRepository<Report.Result> setUpRepository(final Organization organization) {
        final Database server = EbeanServerHelper.getMetricsDatabase();
        final Executor executor = Executors.newSingleThreadExecutor();

        final DatabaseReportExecutionRepository repository = new DatabaseReportExecutionRepository(server, executor);
        final models.ebean.Organization ebeanOrganization = TestBeanFactory.createEbeanOrganization();
        ebeanOrganization.setUuid(organization.getId());
        server.save(ebeanOrganization);

        return repository;
    }

    @Override
    void ensureJobExists(final Organization organization, final UUID jobId) {
        final Database server = EbeanServerHelper.getMetricsDatabase();
        final models.ebean.Organization ebeanOrganization = models.ebean.Organization.findByOrganization(
                server,
                organization
        ).orElseThrow(() -> new IllegalStateException("developer error: test organization must exist"));

        final models.ebean.Report ebeanReport = TestBeanFactory.createEbeanReport(ebeanOrganization);
        ebeanReport.setUuid(jobId);

        // TODO(cbriones): I'm not sure why schedule / source need to be explicitly saved; I would expect a cascade to occur.
        server.save(ebeanReport.getSchedule());
        server.save(ebeanReport.getReportSource());
        server.save(ebeanReport);
    }

    @Override
    Report.Result newResult() {
        return new DefaultReportResult();
    }
}
