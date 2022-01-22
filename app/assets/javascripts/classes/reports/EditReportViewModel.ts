/*
 * Copyright 2019 Dropbox, Inc.
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

import * as ko from 'knockout';
import * as $ from 'jquery';
import uuid from '../Uuid';
import csrf from '../Csrf';

import {
    availableRecipientTypes,
    availableReportFormats,
    availableReportIntervals,
    availableSourceTypes,
    BaseRecipientViewModel,
    BaseScheduleViewModel,
    BaseSourceViewModel,
    RecipientType,
    ReportFormat,
    ScheduleRepetition,
    SourceType,
    ZoneInfo,
} from "./Models";
// @ts-ignore: import is valid
import moment = require('moment-timezone/moment-timezone');

class EditReportViewModel {
    enabled = true;

    id = ko.observable<string>("");
    name = ko.observable<string>("");
    source = new EditSourceViewModel();
    schedule = new EditScheduleViewModel();
    timeout = ko.pureComputed<moment.Duration>(() => moment.duration(this.timeoutString()));

    timeoutString = ko.observable<string>("PT10M");

    // Recipients
    recipients = ko.observableArray<EditRecipientViewModel>();
    existingReport = ko.observable<boolean>(false);

    // Alert for showing error messages on save
    alertMessages = ko.observable<string[]>([]);
    alertHidden = ko.pureComputed<boolean>(() => this.alertMessages().length == 0);

    private static readonly UNKNOWN_ERROR_MESSAGE = "Could not parse response from server.";
    // ^ TODO(anyone): i18n: Ideally this wouldn't be hardcoded, but would be templated into
    //    some early page-load by the server, once it knows the appropriate language.

    activate(id: String) {
        if (id != null) {
            this.loadReport(id);
            return
        } else {
            this.id(uuid.v4());
        }
    }

    loadReport(id: String): void {
        $.getJSON("/v1/reports/" + id, null, (rawReport) => {
            this.id(rawReport.id);
            this.name(rawReport.name);
            this.existingReport(true);
            this.source.load(rawReport.source);
            this.schedule.load(rawReport.schedule);
            this.timeoutString(rawReport.timeout);

            rawReport.recipients.forEach((raw) => {
                const model = new EditRecipientViewModel();
                model.load(raw);
                this.recipients.push(model);
            });
        }).fail((data) => {
            this.alertMessages(EditReportViewModel.parseProblems(data.responseText));
            this.enabled = false;
        });
    }

    compositionComplete() {
        if (!this.enabled) {
            $("#editReportForm :input").attr("disabled", "true");
        }
    }

    addRecipient(recipientType: RecipientType): void {
        let recipient = new EditRecipientViewModel(recipientType);
        this.recipients.push(recipient);
    }

    removeRecipient(index: ko.Observable<number>): void {
        this.recipients.splice(index(), 1);
    }

    save(): void {
        let request: any;
        try {
            request = this.toRequest();
        } catch (e) {
            this.alertMessages([e]);
            return;
        }

        $.ajax({
            type: "PUT",
            url: "/v1/reports",
            beforeSend: function(request) {
                request.setRequestHeader("Csrf-Token", csrf.getToken());
            },
            contentType: "application/json",
            dataType: "json",
            data: JSON.stringify(request),
        })
        .fail((data) => {
            this.alertMessages(EditReportViewModel.parseProblems(data.responseText));
        })
        .done(() => window.location.href = "/#reports");
    }

    cancel(): void {
        window.location.href = "/#reports";
    }

    toRequest(): any {
        return {
            id: this.id(),
            name: this.name(),
            schedule: this.schedule.toRequest(),
            timeout: this.timeout(),
            source: this.source.toRequest(),
            recipients: this.recipients().map((r) => r.toRequest()),
        }
    }

    private static parseProblems(responseJson: string): string[] {
        try {
            return JSON.parse(responseJson).errors;
        } catch (err) {
            return [EditReportViewModel.UNKNOWN_ERROR_MESSAGE];
        }
    }

    private static readonly recipientTypeDisplayNames = {
        [RecipientType.EMAIL]: "Email",
    };

    readonly availableRecipientTypes: {value: RecipientType, text: string}[] = availableRecipientTypes.map(
        recipientType => ({value: recipientType, text: EditReportViewModel.recipientTypeDisplayNames[recipientType]})
    );

    readonly helpMessages = {
        timeout: "Time that can be spent rendering/sending the report before forcibly halting execution. HH:MM:SS or ISO-8601.",
    };
}

class EditRecipientViewModel extends BaseRecipientViewModel {

    static DEFAULT_PDF_WIDTH_INCHES = 8.5;
    static DEFAULT_PDF_HEIGHT_INCHES = 11;

    label(): string {
        return RecipientType[this.type]
    }

    placeholder(): string {
        switch (this.type) {
            case RecipientType.EMAIL:
                return 'example@domain.com'
        }
    }

    toRequest(): any {
        const format: any = {
            type: ReportFormat[this.format()],
        };
        if (this.format() == ReportFormat.PDF) {
            format.widthInches = EditRecipientViewModel.DEFAULT_PDF_WIDTH_INCHES;
            format.heightInches = EditRecipientViewModel.DEFAULT_PDF_HEIGHT_INCHES;
        }
        return {
            type: RecipientType[this.type],
            id: this.id(),
            address: this.address(),
            format: format,
        }
    }

    private static readonly reportFormatDisplayNames = {
        [ReportFormat.HTML]: "HTML",
        [ReportFormat.PDF]: "PDF",
    };

    private static readonly reportFormatHelp = {
        [ReportFormat.HTML]: "<li class='list-group-item'><b>HTML</b> - Hyper Text Markup Language (HTML) tells a " +
                "web browser how to display text, images and other forms of multimedia. Graphical content within " +
                "reports is currently <b>not</b> supported when rendered as HTML.</li>",
        [ReportFormat.PDF]: "<li class='list-group-item'><b>PDF</b> - Portable Document Format (PDF) is a file format " +
                "that has captured all the elements of a document as an electronic image. Graphical content within " +
                "reports is currently supported when rendered as PDF.</li>",
    };

    readonly availableFormats: {value: ReportFormat, text: string}[] = availableReportFormats.map(
        reportFormat => ({value: reportFormat, text: EditRecipientViewModel.reportFormatDisplayNames[reportFormat]})
    );

    readonly helpMessages = {
        format: "The format that the report will be delivered in to the recipient.<br>" +
              "<ul class='list-group'>"+
              availableReportFormats.map(
                      reportFormat => EditRecipientViewModel.reportFormatHelp[reportFormat]
                  )+
              "</ul>",
    };
}

class EditSourceViewModel extends BaseSourceViewModel {
    toRequest(): any {
        let requiredFields = {
            id: this.id(),
            type: SourceType[this.type()],
        };
        switch (this.type()) {
            case SourceType.WEB_PAGE:
                return {
                    ...requiredFields,
                    uri: this.url(),
                    title: this.title(),
                    ignoreCertificateErrors: this.ignoreCertificateErrors(),
                };
            case SourceType.GRAFANA:
                return {
                    ...requiredFields,
                    webPageReportSource: {
                        id: uuid.v4(),
                        type: SourceType[SourceType.WEB_PAGE],
                        uri: this.url(),
                        title: this.title(),
                        ignoreCertificateErrors: this.ignoreCertificateErrors(),
                    },
                };
            default:
                console.error(`unrecognized SourceType: ${this.type()}`);
        }
    }

    // Used by KO data-bind.
    private static readonly sourceTypeDisplayNames = {
        [SourceType.WEB_PAGE]: "Web Page",
        [SourceType.GRAFANA]: "Grafana",
    };
    private static readonly sourceTypeHelp = {
        [SourceType.WEB_PAGE]: "<li class='list-group-item'><b>Browser rendered</b> - The specified URL is loaded in " +
                "the browser and the page content is taken as the rendered report. This <i>only</i> supports static " +
                "content.</li>",
        [SourceType.GRAFANA]: "<li class='list-group-item'><b>Grafana</b> - Specialization of the Web Page source " +
                "for capturing page content from a Grafana based report which may contain dynamic content.</li>",
    };
    readonly availableSourceTypes: {value: SourceType, text: string}[] = availableSourceTypes.map(
        sourceType => ({value: sourceType, text: EditSourceViewModel.sourceTypeDisplayNames[sourceType]})
    );

    readonly helpMessages = {
        type: "The source type determines how a report is generated.<br>" +
              "<ul class='list-group'>"+
              availableSourceTypes.map(
                      sourceType => EditSourceViewModel.sourceTypeHelp[sourceType]
                  )+
              "</ul>",
    };
}

class EditScheduleViewModel extends BaseScheduleViewModel {
    engine: Bloodhound<ZoneInfo>;
    isPeriodic: ko.Computed<boolean>;

    constructor() {
        super();
        const tokenizer = (s: string) => s.toLowerCase().split(/[ \/_()]/);
        const names: [ZoneInfo] = moment.tz.names().map((name) => new ZoneInfo(name));
        this.engine = new Bloodhound({
            local: names,
            datumTokenizer: ({display}) => tokenizer(display),
            queryTokenizer: tokenizer,
        });
        this.engine.initialize();
        this.isPeriodic = ko.pureComputed(() => this.repeat() != ScheduleRepetition.ONE_OFF);
    }

    getAutocompleteOpts(): any {
        // This function exists to defer initialization until the engine has been constructed.
        return {
            source: {
                source: this.engine.ttAdapter(),
                display: ({display}) => display,
            },
            opt: {
                strict: true,
            }
        };
    }

    toRequest(): any {
        // Since we don't actually want to use the local browser timezone
        // we discard it in favor of the explicit 'zone' field on serialization
        const runAtAndAfter = this.start().toISOString(false);
        let runUntil = this.end();
        if (runUntil) {
            runUntil = this.end().toISOString(false);
        }
        const zone = this.zone().value;

        const baseRequest = {
            runAtAndAfter,
            runUntil,
            zone,
        };
        const repeat = this.repeat();
        if (repeat == ScheduleRepetition.ONE_OFF) {
            return Object.assign(baseRequest, {
                type: "ONE_OFF",
            });
        } else {
            let offset = this.offset().toISOString();
            let period = ScheduleRepetition[repeat];
            return Object.assign(baseRequest, {
                type: "PERIODIC",
                period,
                offset,
            });
        }
    }

    private static readonly repeatTypeDisplayNames = {
        [ScheduleRepetition.ONE_OFF]: "Does not repeat",
        [ScheduleRepetition.HOURLY]: "Hourly",
        [ScheduleRepetition.DAILY]: "Daily",
        [ScheduleRepetition.WEEKLY]: "Weekly",
        [ScheduleRepetition.MONTHLY]: "Monthly",
    };

    readonly availableRepeatTypes: {value: ScheduleRepetition, text: string}[] = availableReportIntervals.map(
        repeatType => ({value: repeatType, text: EditScheduleViewModel.repeatTypeDisplayNames[repeatType]})
    );

    readonly helpMessages = {
        offset: "The minimum time to wait after the scheduled time before generating the report. This is commonly " +
                "used to adjust report generation to account for delays from ingestion, aggregation or eventual consistency.",
    };
}

export default EditReportViewModel;
