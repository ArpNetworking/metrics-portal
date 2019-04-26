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
import csrf from '../Csrf';
import DateTimeFormat = Intl.DateTimeFormat;

import {
    RecipientType,
    ReportFormat,
    ScheduleRepetition,
    Source,
    SourceType,
    ZoneInfo,
    Schedule,
} from "./Models";

// @ts-ignore: import is valid
import moment = require('moment-timezone/moment-timezone');

class EditReportViewModel {
    enabled = true;

    id = ko.observable<string>("");
    name = ko.observable<string>("");
    source = new EditSourceViewModel();
    schedule = new EditScheduleViewModel();

    // Recipients
    recipients = ko.observableArray<Recipient>();
    existingReport = ko.observable<boolean>(false);

    // Alert for showing error messages on save
    alertMessage = ko.observable<string>('');
    alertHidden = ko.pureComputed<boolean>(() => this.alertMessage().length == 0);

    private static readonly ERROR_MESSAGE = "There was an error when saving this report.";

    activate(id: String) {
        if (id != null) {
            this.loadReport(id);
            return
        } else {
            this.id(uuid.v4());
        }
    }

    loadReport(id: String): void {
        $.getJSON("/v1/reports/" + id, null, (reportData) => {
            // FIXME(cbriones): Bind these to the view model.
            this.id(reportData.id);
            this.name(reportData.name);
            this.existingReport(true);
            this.source.load(reportData.source);
            this.schedule.load(reportData.schedule);
            const recipients = reportData.recipients.map(Recipient.fromObject);
            this.recipients(recipients);
        }).fail(() => {
            this.alertMessage("Report not found");
            this.enabled = false;
        });
    }

    compositionComplete() {
        if (!this.enabled) {
            $("#editReportForm :input").attr("disabled", "true");
        }
    }

    addRecipient(recipientType: RecipientType): void {
        let recipient = new Recipient(recipientType);
        this.recipients.push(recipient);
    }

    removeRecipient(index: KnockoutObservable<number>): void {
        this.recipients.splice(index(), 1);
    }

    save(): void {
        $.ajax({
            type: "PUT",
            url: "/v1/reports",
            beforeSend: function(request) {
                request.setRequestHeader("Csrf-Token", csrf.getToken());
            },
            contentType: "application/json",
            dataType: "json",
            data: JSON.stringify(this.toRequest())
        })
        .fail(() => {
            this.alertMessage(EditReportViewModel.ERROR_MESSAGE);
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
            source: this.source.toRequest(),
            recipients: this.recipients().map((r) => r.toRequest()),
        }
    }

    readonly availableRecipientTypes = [
        {value: RecipientType.Email,  text: "Email"},
    ];
}

class Recipient {
    id: KnockoutObservable<string>;
    type: RecipientType;
    address: KnockoutObservable<string>;
    format: KnockoutObservable<ReportFormat>;

    constructor(type: RecipientType) {
        this.id = ko.observable(uuid.v4());
        this.type = type;
        this.address = ko.observable("");
        this.format = ko.observable(ReportFormat.Pdf);
    }

    public static fromObject(raw): Recipient {
        const type = RecipientType.Email;

        const recipient = new Recipient(type);
        recipient.id(raw.id);
        recipient.address(raw.address)

        let format;
        if (raw.format.type == "Html") {
            format = ReportFormat.Html;
        } else if (raw.format.type == "Pdf") {
            format = ReportFormat.Pdf;
        } else {
            throw new Error(`Unknown format "${raw.format.type}"`);
        }

        recipient.format(format);
        return recipient;
    }

    label(): string {
        return RecipientType[this.type]
    }

    placeholder(): string {
        switch (this.type) {
            case RecipientType.Email:
                return 'example@domain.com'
        }
    }

    toRequest(): any {
        return {
            type: RecipientType[this.type],
            id: this.id(),
            address: this.address(),
            formats: [
                {
                    type: ReportFormat[this.format()],
                },
            ],
        }
    }

    readonly availableFormats = [
        {value: ReportFormat.Pdf,  text: "PDF"},
        {value: ReportFormat.Html,  text: "HTML"},
    ];

    readonly helpMessages = {
        format: "The format that will be delivered to this recipient. For example, a PDF attached to an email or " +
            "some HTML rendered inline."
    };
}

class EditSourceViewModel {
    model: Source;

    constructor() {
        this.model = new Source();
    }

    load(raw) {
        this.model = Source.fromObject(raw);
    }

    toRequest() {
        let requestType;
        if (this.model.type() == SourceType.ChromeScreenshot) {
            requestType = "CHROME_SCREENSHOT"
        }
        return {
            type: requestType,
            id: this.model.id(),
            uri: this.model.url(),
            title: this.model.title(),
            ignoreCertificateErrors: this.model.ignoreCertificateErrors(),
            triggeringEventName: this.model.eventName(),
        }
    }

    // Used by KO data-bind.
    readonly availableSourceTypes = [
        {value: SourceType.ChromeScreenshot,  text: "Browser rendered"},
    ];

    readonly helpMessages = {
        type: "The source type determines how a report is generated.<br>" +
              "<ul class='list-group'>"+
              "<li class='list-group-item'><b>Browser rendered</b> - A URL is loaded in the browser and the rendered " +
              "contents of the page are taken as the generated report.</li>" +
              "</ul>",
        eventName: "When an event with this name is triggered, the report is considered fully rendered.",
    };
}

class EditScheduleViewModel {
    model: Schedule;
    engine: Bloodhound<ZoneInfo>;

    constructor() {
        this.model = new Schedule();
        const tokenizer = (s: string) => s.toLowerCase().split(/[ \/_()]/);
        const names: [ZoneInfo] = moment.tz.names().map((name) => new ZoneInfo(name));
        this.engine = new Bloodhound({
            local: names,
            datumTokenizer: ({display}) => tokenizer(display),
            queryTokenizer: tokenizer,
        });
        this.engine.initialize();
    }

    load(raw) {
        this.model = Schedule.fromObject(raw);
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
        const runAtAndAfter = this.model.start().toISOString(false);
        let runUntil = this.model.end();
        if (runUntil) {
            runUntil = this.model.end().toISOString(false);
        }
        const zone = this.model.zone().value;

        const baseRequest = {
            runAtAndAfter,
            runUntil,
            zone,
        };
        const repeat = this.model.repeat();
        if (repeat == ScheduleRepetition.OneOff) {
            return Object.assign(baseRequest, {
                type: "OneOff",
            });
        } else {
            let offset = this.model.offset().toISOString();
            let period = ScheduleRepetition[repeat];
            return Object.assign(baseRequest, {
                type: "Periodic",
                period,
                offset,
            });
        }
    }

    readonly availableRepeatTypes = [
        {value: ScheduleRepetition.OneOff,  text: "Does not repeat"},
        {value: ScheduleRepetition.Hourly,  text: "Hourly"},
        {value: ScheduleRepetition.Daily,   text: "Daily"},
        {value: ScheduleRepetition.Weekly,  text: "Weekly"},
        {value: ScheduleRepetition.Monthly, text: "Monthly"},
    ];

    readonly helpMessages = {
        offset: "This is the smallest amount of time to wait after the start of a period before generating the report.",
    };
}

export = EditReportViewModel;
