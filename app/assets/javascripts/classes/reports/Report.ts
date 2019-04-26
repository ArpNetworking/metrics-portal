/*
 * Copyright 2018 Dropbox, Inc.
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
import uuid = require('../Uuid');
import moment = require('moment-timezone/moment-timezone');

export default class Report {
    id: string;
    name: string;
    editUri: string;

    schedule: Schedule;
    source: Source;
    recipients: Recipient[];

    // recipients
    constructor(id: string, name: string, source: any, schedule: any, recipients: object[]) {
        this.id = id;
        this.name = name;
        this.editUri = `#report/edit/${this.id}`;

        this.recipients = recipients.map(Recipient.fromObject);
        this.schedule = Schedule.fromObject(schedule);
        this.source = Source.fromObject(source);
    }
}

enum RecipientType {
    Email,
}

enum ReportFormat {
    Pdf,
    Html
}

class Recipient {
    id: KnockoutObservable<string>;
    type: RecipientType;
    address: KnockoutObservable<string>;
    format: KnockoutObservable<ReportFormat>;
    badgeText: KnockoutComputed<string>;

    constructor(type: RecipientType) {
        this.id = ko.observable(uuid.v4());
        this.type = type;
        this.address = ko.observable("");
        this.format = ko.observable(ReportFormat.Pdf);
        this.badgeText = ko.computed(() =>
            `${this.address()} (${ReportFormat[this.format()].toUpperCase()})`
        );
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
}

enum SourceType {
    ChromeScreenshot,
}

class Source {
    id = ko.observable(uuid.v4());
    type = ko.observable<SourceType>(SourceType.ChromeScreenshot);
    title = ko.observable<string>("");
    url = ko.observable<string>("");
    eventName = ko.observable<string>("");
    ignoreCertificateErrors = ko.observable<boolean>(false);
    displayText = ko.computed<string>(() => {
        const type = "Browser rendered";
        return `${this.title()} (${type})`
    });

    public static fromObject(raw): Source {
        const source = new Source();
        source.id(raw.id);
        source.url(raw.uri);
        source.title(raw.title);
        source.eventName(raw.triggeringEventName);
        source.ignoreCertificateErrors(raw.ignoreCertificateErrors);
        // FIXME(cbriones)
        // source.type = ko.observable<SourceType>(SourceType.ChromeScreenshot);
        return source;
    }
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

    public abbr(): string {
        return moment.tz(this.value).zoneAbbr();
    }
}

class Schedule {
    repeat = ko.observable<ScheduleRepetition>(ScheduleRepetition.OneOff);
    start = ko.observable<moment.Moment>();
    end = ko.observable<moment.Moment | undefined>(undefined);
    offsetString = ko.observable<string>("");
    offset = ko.pureComputed<moment.Duration>(() => moment.duration(this.offsetString()));
    zone = ko.observable<ZoneInfo>(new ZoneInfo(moment.tz.guess()));

    displayType = ko.computed(() => {
        switch (this.repeat()) {
            case ScheduleRepetition.OneOff:
                return "One-off";
            case ScheduleRepetition.Daily:
                return "Daily";
            case ScheduleRepetition.Hourly:
                return "Hourly";
            case ScheduleRepetition.Monthly:
                return "Monthly";
            case ScheduleRepetition.Weekly:
                return "Weekly";
        }
    });

    label = ko.computed(() => {
        const label = [];
        switch (this.repeat()) {
            case ScheduleRepetition.OneOff:
                return "asdf"
                // return `Once at ${this.start().toDate()} (${this.zone().abbr()})`;
            case ScheduleRepetition.Daily:
            case ScheduleRepetition.Hourly:
            case ScheduleRepetition.Monthly:
            case ScheduleRepetition.Weekly:
                const desc = ScheduleRepetition[this.repeat()].toLowerCase();
                let start = this.start();
                if (start !== undefined) {
                    label.push(`Repeats ${desc} from ${start.toString()}`)
                }
                let end = this.end();
                if (end !== undefined) {
                    label.push(` to ${end.toString()}`)
                }
        }
        return label.join("");
    });

    constructor() {}

    public static fromObject(raw): Schedule {
        let schedule = new Schedule();
        let repeat = ScheduleRepetition.OneOff;
        if (raw.type == "Periodic") {
            switch (raw.period) {
                case "Hourly":
                    repeat = ScheduleRepetition.Hourly;
                    break;
                case "Daily":
                    repeat = ScheduleRepetition.Daily;
                    break;
                case "Monthly":
                    repeat = ScheduleRepetition.Monthly;
                    break;
            }
        }
        schedule.repeat(repeat);
        schedule.offsetString(raw.offset);
        schedule.zone(new ZoneInfo(raw.zone));
        schedule.start(moment(raw.runAtAndAfter));
        schedule.end(moment(raw.runUntil));
        return schedule;
    }
}
