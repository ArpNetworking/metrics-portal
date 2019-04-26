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

export class Source {
    id = ko.observable("");
    type = ko.observable<SourceType>(SourceType.ChromeScreenshot);
    title = ko.observable<string>("");
    url = ko.observable<string>("");
    eventName = ko.observable<string>("");
    ignoreCertificateErrors = ko.observable<boolean>(false);

    public static fromObject(raw): Source {
        const source = new Source();

        source.id(raw.id);
        source.url(raw.uri);
        source.title(raw.title);
        source.eventName(raw.triggeringEventName);
        source.ignoreCertificateErrors(raw.ignoreCertificateErrors);
        // FIXME(cbriones)
        // type = ko.observable<SourceType>(SourceType.ChromeScreenshot);
        return source
    }
}

export class Schedule {
    repeat = ko.observable<ScheduleRepetition>(ScheduleRepetition.OneOff);
    start = ko.observable<moment.Moment>();
    end = ko.observable<moment.Moment | undefined>(undefined);
    offset = ko.pureComputed<moment.Duration>(() => moment.duration(this.offsetString()));
    zone = ko.observable<ZoneInfo>(new ZoneInfo(moment.tz.guess()));

    offsetString = ko.observable<string>("");

    public static fromObject(raw: any): Schedule {
        const schedule = new Schedule();

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

export class Recipient {
    id: KnockoutObservable<string>;
    type: RecipientType;
    address: KnockoutObservable<string>;
    format: KnockoutObservable<ReportFormat>;

    constructor(id: string, type: RecipientType) {
        this.id = ko.observable(id);
        this.type = type;
        this.address = ko.observable("");
        this.format = ko.observable(ReportFormat.Pdf);
    }

    public static fromObject(raw): Recipient {
        const type = RecipientType.Email;

        const recipient = new Recipient(raw.id, type);
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
