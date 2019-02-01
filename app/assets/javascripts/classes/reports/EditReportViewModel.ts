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

import ReportData = require('./Report');
import ko = require('knockout');
import $ = require('jquery');
import uuid = require('../Uuid');
import csrf from '../Csrf';
import DateTimeFormat = Intl.DateTimeFormat;

import * as _ from 'underscore';

import moment = require('moment-timezone/moment-timezone');

class EditReportViewModel {
    id = ko.observable<string>("");
    name = ko.observable<string>("");
    source = new EditSourceViewModel();
    schedule = new EditScheduleViewModel();

    // Recipients
    recipients = ko.observableArray<Recipient>();
    existingReport = ko.observable<boolean>(false);

    activate(id: String) {
        if (id != null) {
            this.loadReport(id);
            return
        } else {
            this.id(uuid.v4());
        }
    }

    loadReport(id: String): void {
        $.getJSON("/v1/reports/" + id, {}, (data) => {
            // TODO(cbriones): Bind these to the view model.
            this.existingReport(true);
        });
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
        }).done(() => {
            window.location.href = "/#reports";
        });
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

enum RecipientType {
    Email,
}

enum ReportFormat {
    Pdf,
    Html,
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
            type: RecipientType[this.type].toUpperCase(),
            id: this.id(),
            address: this.address(),
            formats: [
                {
                    type: ReportFormat[this.format()].toUpperCase(),
                }
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

enum SourceType {
    ChromeScreenshot,
}

class EditSourceViewModel {
    id = ko.observable(uuid.v4());
    type = ko.observable<SourceType>(SourceType.ChromeScreenshot);
    title = ko.observable<string>("");
    url = ko.observable<string>("");
    eventName = ko.observable<string>("");
    ignoreCertificateErrors = ko.observable<boolean>(false);

    toRequest() {
        let requestType;
        if (this.type() == SourceType.ChromeScreenshot) {
            requestType = "CHROME_SCREENSHOT"
        }
        return {
            type: requestType,
            id: this.id(),
            uri: this.url(),
            title: this.title(),
            ignoreCertificateErrors: this.ignoreCertificateErrors(),
            triggeringEventName: this.eventName(),
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

enum ScheduleRepetition {
    OneOff,
    Hourly,
    Daily,
    Weekly,
    Monthly
}

class ZoneInfo {
    value: string;
    display: string;

    constructor(value: string) {
        this.value = value;
        this.display = `${this.value} (${moment.tz(this.value).zoneAbbr()})`;
    }
}

class EditScheduleViewModel {
    repeat = ko.observable<ScheduleRepetition>(ScheduleRepetition.OneOff);
    start = ko.observable<moment.Moment>();
    end = ko.observable<moment.Moment | undefined>(undefined);
    offsetString = ko.observable<string>("");
    offset = ko.pureComputed<moment.Duration>(() => moment.duration(this.offsetString()));
    zone = ko.observable<ZoneInfo>(new ZoneInfo(moment.tz.guess()));
    engine: Bloodhound<ZoneInfo>;
    isPeriodic = ko.pureComputed<boolean>(() => this.repeat() != ScheduleRepetition.OneOff);

    constructor() {
        const tokenizer = (s: string) => s.toLowerCase().split(/[ \/_()]/);
        const names: [ZoneInfo] = moment.tz.names().map((name) => new ZoneInfo(name));
        this.engine = new Bloodhound({
            local: names,
            datumTokenizer: ({display}) => tokenizer(display),
            queryTokenizer: tokenizer,
        });
        this.engine.initialize();
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
        if (repeat == ScheduleRepetition.OneOff) {
            return Object.assign(baseRequest, {
                type: "ONE_OFF",
            });
        } else {
            let offset = this.offset().toISOString();
            let period = ScheduleRepetition[repeat].toUpperCase();
            return Object.assign(baseRequest, {
                type: "PERIODIC",
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
