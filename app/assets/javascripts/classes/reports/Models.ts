import uuid = require('../Uuid');
import ko = require('knockout');
// @ts-ignore: import is valid
import moment = require('moment-timezone/moment-timezone');

export enum RecipientType {
    Email,
}

export enum ReportFormat {
    Pdf,
    Html,
}

export enum SourceType {
    ChromeScreenshot,
}

export enum ScheduleRepetition {
    OneOff,
    Hourly,
    Daily,
    Weekly,
    Monthly,
}

export class ZoneInfo {
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

export class BaseSourceViewModel {
    id = ko.observable("");
    type = ko.observable<SourceType>(SourceType.ChromeScreenshot);
    title = ko.observable<string>("");
    url = ko.observable<string>("");
    eventName = ko.observable<string>("");
    ignoreCertificateErrors = ko.observable<boolean>(false);

    public load(raw): this {
        this.id(raw.id);
        this.url(raw.uri);
        this.title(raw.title);
        this.eventName(raw.triggeringEventName);
        this.ignoreCertificateErrors(raw.ignoreCertificateErrors);
        switch (raw.type) {
            case "ChromeScreenshot":
                this.type(SourceType.ChromeScreenshot);
                break;
            default:
                throw new Error(`Unknown source type: ${raw.type}`);
        }
        return this;
    }
}

export class BaseScheduleViewModel {
    repeat = ko.observable<ScheduleRepetition>(ScheduleRepetition.OneOff);
    start = ko.observable<moment.Moment>();
    end = ko.observable<moment.Moment | undefined>(undefined);
    offset = ko.pureComputed<moment.Duration>(() => moment.duration(this.offsetString()));
    zone = ko.observable<ZoneInfo>(new ZoneInfo(moment.tz.guess()));

    offsetString = ko.observable<string>("");

    public load(raw: any): this {
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
                default:
                    throw new Error(`Unknown schedule period: ${raw.period}`);
            }
        }
        this.repeat(repeat);
        this.offsetString(raw.offset);
        this.zone(new ZoneInfo(raw.zone));
        this.start(moment(raw.runAtAndAfter));
        this.end(moment(raw.runUntil));
        return this;
    }
}

export class BaseRecipientViewModel {
    id: KnockoutObservable<string>;
    type: RecipientType;
    address: KnockoutObservable<string>;
    format: KnockoutObservable<ReportFormat>;

    constructor(type?: RecipientType) {
        this.id = ko.observable(uuid.v4());
        this.type = type || RecipientType.Email;
        this.address = ko.observable("");
        this.format = ko.observable(ReportFormat.Pdf);
    }

    public load(raw): this {
        this.address(raw.address);

        switch (raw.type) {
            case "Email":
                this.type = RecipientType.Email;
                break;
            default:
                throw new Error(`Unknown recipient type: ${raw.type}`)
        }

        if (raw.format.type == "Html") {
            this.format(ReportFormat.Html);
        } else if (raw.format.type == "Pdf") {
            this.format(ReportFormat.Pdf);
        } else {
            throw new Error(`Unknown format "${raw.format.type}"`);
        }
        return this;
    }
}
