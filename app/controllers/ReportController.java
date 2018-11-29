/*
 * Copyright 2015 Groupon.com
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

import com.arpnetworking.metrics.portal.reports.EmailBuilder;
import com.arpnetworking.metrics.portal.reports.Scraper;
import com.github.kklisura.cdt.services.ChromeDevToolsService;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import play.mvc.Controller;
import play.mvc.Result;

import javax.inject.Singleton;
import javax.mail.MessagingException;
import javax.mail.Transport;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Metrics portal alert controller. Exposes APIs to query and manipulate alerts.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
@Singleton
public class ReportController extends Controller {

    private static long SCREENSHOT_TIMEOUT_MS = 10000;

    // TODO: store report definitions in an actual database instead of hardcoding them.
    private static class Report {
        public String recipient;
        public String subjectLine;
        public String grafanaReportPanelUrl;
        public Scraper.Dimensions pdfSizeInches;

        public Report(String recipient, String subjectLine, String grafanaReportPanelUrl, Scraper.Dimensions pdfSizeInches) {
            this.recipient = recipient;
            this.subjectLine = subjectLine;
            this.grafanaReportPanelUrl = grafanaReportPanelUrl;
            this.pdfSizeInches = pdfSizeInches;
        }
    }
    private static Map<String, Report> REPORT_ID_TO_GRAFANA_REPORT_URL = new HashMap<>();
    static {
        REPORT_ID_TO_GRAFANA_REPORT_URL.put(
                "webperf-demo",
                new Report(
                        "spencerpearson@dropbox.com",
                        "Demo Webperf Report",
                        "https://localhost:9450/d/tdJITcBmz/playground?panelId=2&fullscreen&orgId=1&theme=light",
                        new Scraper.Dimensions(8.5, 30)
                )
        );
    }

    /**
     * Public constructor.
     *
     * @param configuration Instance of Play's {@link Config}.
     */
    @Inject
    public ReportController(
            final Config configuration) {
    }

    /**
     * Executes a report and emails a snapshot of the Grafana report panel to its recipients.
     *
     * @param id id of the report to run
     * @return Ok if the alert was created or updated successfully, a failure HTTP status code otherwise.
     */
    public Result run(String id) {
        Report report = REPORT_ID_TO_GRAFANA_REPORT_URL.get(id);
        if (report == null) return notFound("no report has id="+id);

        final ChromeDevToolsService devToolsService = Scraper.createDevToolsService(true);
        final Optional<Scraper.Snapshot> snapshot = Scraper.takeGrafanaReportScreenshot(
                devToolsService,
                report.grafanaReportPanelUrl,
                report.pdfSizeInches,
                SCREENSHOT_TIMEOUT_MS
        );
        if (!snapshot.isPresent()) return internalServerError("timed out while taking snapshot");
        try {
            Transport.send(EmailBuilder.buildImageEmail(
                    report.recipient,
                    report.subjectLine,
                    snapshot.get().html,
                    snapshot.get().pdf
            ));
        } catch (MessagingException e) {
            return internalServerError("failed building/sending message: "+e);
        }
        return ok("sent");
    }
}
