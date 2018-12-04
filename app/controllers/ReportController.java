/*
 * Copyright 2018 Dropbox
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
package controllers;

import akka.actor.ActorRef;
import com.arpnetworking.metrics.portal.reports.*;
import com.arpnetworking.metrics.portal.reports.impl.EmailReportSink;
import com.arpnetworking.metrics.portal.reports.impl.GrafanaReportPanelScreenshotReportGenerator;
import com.arpnetworking.metrics.portal.reports.impl.ReportScheduler;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import play.mvc.Controller;
import play.mvc.Result;

import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Metrics portal alert controller. Exposes APIs to query and manipulate alerts.
 *
 */
@Singleton
public class ReportController extends Controller {

    private ActorRef scheduler;
    private ReportRepository repository;

    /**
     * Public constructor.
     *
     * @param scheduler The reports.ReportScheduler
     * @param configuration Instance of Play's {@link Config}.
     */
    @Inject
    public ReportController(
            @Named("ReportScheduler") ActorRef scheduler,
            ReportRepository repository,
            final Config configuration) {
        this.scheduler = scheduler;
        this.repository = repository;
    }

    /**
     * Creates a new report.
     *
     * @return Ok if the alert was created or updated successfully, a failure HTTP status code otherwise.
     */
    public Result createEmailedGrafanaReport() {
        try {
            final Map<String, String[]> params = request().queryString();
            String name = params.get("name")[0];
            int periodMinutes = Integer.parseInt(params.get("periodMinutes")[0]);
            String recipient = params.get("recipient")[0];
            String subject = params.get("subject")[0];
            String reportUrl = params.get("reportUrl")[0];
            double pdfHeightInches = Double.parseDouble(params.get("pdfHeightInches")[0]);

            String id = repository.add(
                    new ReportSpec(
                            name,
                            new GrafanaReportPanelScreenshotReportGenerator(
                                    reportUrl,
                                    true,
                                    10000,
                                    8.5,
                                    pdfHeightInches
                            ),
                            new EmailReportSink(recipient, subject)
                    )
            );

            scheduler.tell(new ReportScheduler.Schedule(new ReportScheduler.Job(id, Instant.now(), java.time.Duration.of(periodMinutes, ChronoUnit.MINUTES))), null);
            return ok(id);
        } catch (Exception e) {
            return internalServerError("augh: "+e);
        }
    }

    /**
     * Executes a report and emails a snapshot of the Grafana report panel to its recipients.
     *
     * @param id id of the report to run
     * @return Ok if the alert was created or updated successfully, a failure HTTP status code otherwise.
     */
    public Result run(String id) {
        System.out.println("running");
        ReportSpec spec = this.repository.getSpec(id);
        if (spec == null) return notFound("no report has id="+id);
        try {
            spec.run();
        } catch (Exception e) {
            return internalServerError();
        }
        return ok("Did it!");
    }
}
