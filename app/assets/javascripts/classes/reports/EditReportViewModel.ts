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

import ko = require('knockout');
import $ = require('jquery');
import uuid = require('../Uuid');
// @ts-ignore: import is valid
import moment = require('moment-timezone/moment-timezone');
import csrf from '../Csrf';

import {
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

    removeRecipient(index: KnockoutObservable<number>): void {
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

    readonly availableRecipientTypes = [
        {value: RecipientType.EMAIL,  text: "Email"},
    ];

    readonly helpMessages = {
        timeout: "Time that can be spent rendering/sending the report before forcibly halting execution. HH:MM:SS or ISO-8601.",
    }

    private static parseProblems(responseJson: string): string[] {
        try {
            return JSON.parse(responseJson).errors;
        } catch (err) {
            return [EditReportViewModel.UNKNOWN_ERROR_MESSAGE];
        }
    }
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

    readonly availableFormats = [
        {value: ReportFormat.PDF,  text: "PDF"},
        {value: ReportFormat.HTML,  text: "HTML"},
    ];

    readonly helpMessages = {
        format: "The format that will be delivered to this recipient. For example, a PDF attached to an email or " +
            "some HTML rendered inline.",
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
                    triggeringEventName: this.eventName(),
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
                        triggeringEventName: this.eventName(),
                    },
                };
            default:
                console.error(`unrecognized SourceType: ${this.type()}`);
        }
    }

    // Used by KO data-bind.
    private static readonly sourceTypeDisplayNames = {
        [SourceType.WEB_PAGE]: "Web page",
        [SourceType.GRAFANA]: "Grafana",
    };
    readonly availableSourceTypes: {value: SourceType, text: string}[] = availableSourceTypes.map(
        sourceType => ({value: sourceType, text: EditSourceViewModel.sourceTypeDisplayNames[sourceType]})
    );

    readonly helpMessages = {
        type: "The source type determines how a report is generated.<br>" +
              "<ul class='list-group'>"+
              "<li class='list-group-item'><b>Browser rendered</b> - A URL is loaded in the browser and the rendered " +
              "contents of the page are taken as the generated report.</li>" +
              "<li class='list-group-item'><b>Grafana</b> - Like browser-rendered, but tweaked to pull data from a Grafana Report panel" +
              " (which are loaded asynchronously after page-load, hence needing the specialization).</li>" +
              "</ul>",
        eventName: "When an event with this name is triggered, the report is considered fully rendered.",
    };
}

class EditScheduleViewModel extends BaseScheduleViewModel {
    engine: Bloodhound<ZoneInfo>;
    isPeriodic: KnockoutComputed<boolean>;

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

    readonly availableRepeatTypes = [
        {value: ScheduleRepetition.ONE_OFF, text: "Does not repeat"},
        {value: ScheduleRepetition.HOURLY,  text: "Hourly"},
        {value: ScheduleRepetition.DAILY,   text: "Daily"},
        {value: ScheduleRepetition.WEEKLY,  text: "Weekly"},
        {value: ScheduleRepetition.MONTHLY, text: "Monthly"},
    ];

    readonly helpMessages = {
        offset: "This is the smallest amount of time to wait after the start of a period before generating the report.",
    };
}

export = EditReportViewModel;
