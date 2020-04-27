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
package com.arpnetworking.metrics.portal.reports.impl;

import com.arpnetworking.metrics.portal.alerts.impl.AbstractDatabaseExecutionRepository;
import com.arpnetworking.metrics.portal.scheduling.JobExecutionRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import io.ebean.EbeanServer;
import io.ebean.ExpressionList;
import models.ebean.ReportExecution2;
import models.internal.Organization;
import models.internal.reports.Report;

import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityNotFoundException;

/**
 * Implementation of {@link JobExecutionRepository} for {@link Report} jobs using a SQL database.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public final class AlertExecutionRepository extends AbstractDatabaseExecutionRepository<Report.Result, ReportExecution2> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertExecutionRepository.class);
    private final EbeanServer _ebeanServer;

    /**
     * Public constructor.
     *
     * @param ebeanServer Play's {@code EbeanServer} for this repository.
     */
    @Inject
    public AlertExecutionRepository(@Named("metrics_portal") final EbeanServer ebeanServer) {
        super(ebeanServer, LOGGER, new ReportExecutionAdapter(ebeanServer));
        _ebeanServer = ebeanServer;
    }

    private static final class ReportExecutionAdapter implements AbstractDatabaseExecutionRepository.ExecutionAdapter<Report.Result, ReportExecution2> {
        private final EbeanServer _ebeanServer;

        public ReportExecutionAdapter(final EbeanServer ebeanServer) {
            _ebeanServer = ebeanServer;
        }

        @Override
        public ExpressionList<ReportExecution2> findOneQuery(final UUID jobId, final Organization organization) {
            final Optional<models.ebean.Report> report = models.ebean.Organization.findByOrganization(_ebeanServer, organization)
                    .flatMap(beanOrg -> models.ebean.Report.findByUUID(
                            _ebeanServer,
                            beanOrg,
                            jobId
                    ));
            if (!report.isPresent()) {
                final String message = String.format(
                        "Could not find report with uuid=%s, organization.uuid=%s",
                        jobId,
                        organization.getId()
                );
                throw new EntityNotFoundException(message);
            }
            return _ebeanServer.createQuery(ReportExecution2.class)
                    .where()
                    .eq("report", report.get());
        }

        @Override
        public ReportExecution2 newExecution(final UUID jobId, final Organization org) {
            // FIXME: This shouldn't need to perform a double lookup.
            final Optional<models.ebean.Report> report = models.ebean.Organization.findByOrganization(_ebeanServer, org)
                    .flatMap(beanOrg -> models.ebean.Report.findByUUID(
                            _ebeanServer,
                            beanOrg,
                            jobId
                    ));
            if (!report.isPresent()) {
                final String message = String.format(
                        "Could not find report with uuid=%s, organization.uuid=%s",
                        jobId,
                        org.getId()
                );
                throw new EntityNotFoundException(message);
            }
            final ReportExecution2 execution = new ReportExecution2();
            execution.setReport(report.get());
            return execution;
        }
    }
}
