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
import com.arpnetworking.metrics.portal.reports.impl.GrafanaScreenshotReportSpec;
import com.arpnetworking.metrics.portal.reports.impl.ReportScheduler;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import org.simplejavamail.mailer.Mailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Controller;
import play.mvc.Result;

import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Metrics portal alert controller. Exposes APIs to query and manipulate alerts.
 *
 */
@Singleton
public class ReportController extends Controller {

    private ActorRef scheduler;
    private JobRepository repository;
    private Mailer mailer;

    /**
     * Public constructor.
     *
     * @param scheduler The reports.ReportScheduler
     * @param configuration Instance of Play's {@link Config}.
     */
    @Inject
    public ReportController(
            @Named("ReportScheduler") ActorRef scheduler,
            JobRepository repository,
            final Config configuration,
            Mailer mailer) {
        this.scheduler = scheduler;
        this.repository = repository;
        this.mailer = mailer;
    }

    /**
     * List all report ids.
     *
     * @return Ok, with newline-separated report ids
     */
    public Result list() {
        return ok(String.join("\n", repository.listSpecs().collect(Collectors.toList())));
    }

    /**
     * Creates a new report.
     *
     * @return Ok if the alert was created or updated successfully, a failure HTTP status code otherwise.
     */
    public Result createEmailedGrafanaReport(
            String name,
            int periodMinutes,
            String recipient,
            String title,
            String reportUrl,
            double pdfHeightInches) {
        try {
            String id = repository.add(
                    new Job(
                            new GrafanaScreenshotReportSpec(
                                    reportUrl,
                                    title,
                                    true,
                                    Duration.of(10, ChronoUnit.SECONDS),
                                    8.5,
                                    pdfHeightInches
                            ),
                            new EmailReportSink(recipient, mailer),
                            java.time.Duration.of(periodMinutes, ChronoUnit.MINUTES)
                    )
            );

            scheduler.tell(
                    new ReportScheduler.Schedule(new ReportScheduler.ScheduledJob(Instant.now(), id)),
                    null
            );
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
    public CompletionStage<Result> run(String id) {
        Job j = this.repository.get(id);
        if (j == null) {
            return CompletableFuture.completedFuture(notFound());
        }

        return j.getSpec().render()
                .thenCompose(r -> j.getSink().send(r))
                .handle((nothing, err) -> {
                    if (err != null) {
                        LOGGER.error("Failed running job id=%s: %s", id, err);
                        return internalServerError();
                    }
                    return ok("ran job id="+id);
                });
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportController.class);
}
