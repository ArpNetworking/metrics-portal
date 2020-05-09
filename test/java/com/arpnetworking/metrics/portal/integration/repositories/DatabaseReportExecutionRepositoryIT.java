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
import com.google.common.base.Throwables;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.ebean.EbeanServer;
import models.internal.Organization;
import models.internal.impl.DefaultReportResult;
import models.internal.reports.Report;
import models.internal.scheduling.JobExecution;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Integration tests for {@link DatabaseReportExecutionRepository}.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class DatabaseReportExecutionRepositoryIT extends JobExecutionRepositoryIT<Report.Result> {
    @Override
    JobExecutionRepository<Report.Result> setUp(final Organization organization, final UUID jobId) {
        final EbeanServer server = EbeanServerHelper.getMetricsDatabase();
        final DatabaseReportExecutionRepository _repository = new DatabaseReportExecutionRepository(server);
        final models.ebean.Organization ebeanOrganization = TestBeanFactory.createEbeanOrganization();
        ebeanOrganization.setUuid(organization.getId());
        server.save(ebeanOrganization);

        final models.ebean.Report ebeanReport = TestBeanFactory.createEbeanReport(ebeanOrganization);
        ebeanReport.setUuid(jobId);

        // TODO(cbriones): I'm not sure why schedule / source need to be explicitly saved; I would expect a cascade to occur.
        server.save(ebeanReport.getSchedule());
        server.save(ebeanReport.getReportSource());
        server.save(ebeanReport);

        // TODO(cbriones): Create a partition for the current time.
        return _repository;
    }

    @Override
    Report.Result newResult() {
        return new DefaultReportResult();
    }
}
