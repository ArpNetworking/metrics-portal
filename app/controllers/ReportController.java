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

import akka.japi.Pair;
import com.arpnetworking.metrics.portal.reports.*;
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
 */
@Singleton
public class ReportController extends Controller {

    private static long SCREENSHOT_TIMEOUT_MS = 10000;

    private static class ReportSpec {
        public ReportGenerator generator;
        public ReportSink sink;

        public ReportSpec(ReportGenerator generator, ReportSink sink) {
            this.generator = generator;
            this.sink = sink;
        }
    }

    // TODO: store report definitions in an actual database instead of hardcoding them.
    private static Map<String, ReportSpec> REPORT_DEFNS = new HashMap<>();
    static {
        REPORT_DEFNS.put(
                "webperf-demo",
                new ReportSpec(
                        new GrafanaReportPanelScreenshotReportGenerator(
                                "https://localhost:9450/d/tdJITcBmz/playground?panelId=2&fullscreen&orgId=1&theme=light",
                                true,
                                10000,
                                8.5,
                                30
                        ),
                        new EmailReportSink("spencerpearson@dropbox.com", "Demo Webperf Report")
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
        ReportSpec spec = REPORT_DEFNS.get(id);
        if (spec == null) return notFound("no report has id="+id);

        final Map<String,String[]> params = request().queryString();
        if (params.get("sendEmail") != null && params.get("sendEmail")[0].equals("true")) {

        }

        Report r = null;
        try {
            r = spec.generator.generateReport();
        } catch (Exception e) {
            r = new Report("Report '"+id+"' failed", "Reason: <pre>"+e+"</pre>", null);
        }
        try {
            spec.sink.send(r);
        } catch (Exception e) {
            return internalServerError("unable to send report");
        }

        return ok("sent");
    }
}
