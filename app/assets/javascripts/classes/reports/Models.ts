import uuid = require('../Uuid');
import ko = require('knockout');
// @ts-ignore: import is valid
import moment = require('moment-timezone/moment-timezone');

export enum RecipientType {
    EMAIL,
}

export enum ReportFormat {
    PDF,
    HTML,
}

export enum SourceType {
    WEB_PAGE,
    GRAFANA,
}

export enum ScheduleRepetition {
    ONE_OFF,
    HOURLY,
    DAILY,
    WEEKLY,
    MONTHLY,
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
    id = ko.observable(uuid.v4());
    type = ko.observable<SourceType>(SourceType.WEB_PAGE);
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

        const type = SourceType[raw.type as string];
        if (type === undefined) {
            throw new Error(`Unknown source type: ${raw.type}`);
        }
        this.type(type);
        return this;
    }
}

export class BaseScheduleViewModel {
    repeat = ko.observable<ScheduleRepetition>(ScheduleRepetition.ONE_OFF);
    start = ko.observable<moment.Moment>();
    end = ko.observable<moment.Moment | undefined>(undefined);
    offset = ko.pureComputed<moment.Duration>(() => moment.duration(this.offsetString()));
    zone = ko.observable<ZoneInfo>(new ZoneInfo(moment.tz.guess()));

    offsetString = ko.observable<string>("");

    public load(raw: any): this {
        this.start(moment(raw.runAtAndAfter));
        if (raw.type == "ONE_OFF") {
            this.repeat(ScheduleRepetition.ONE_OFF)
        } else if (raw.type == "PERIODIC") {
            const repeat = raw.period && ScheduleRepetition[raw.period as string];
            if (repeat === undefined || repeat == ScheduleRepetition.ONE_OFF) {
                throw new Error(`Invalid schedule period: ${raw.period}`);
            }
            this.repeat(repeat);
            this.zone(new ZoneInfo(raw.zone));
            this.offsetString(raw.offset);
            if (raw.runUntil) {
                this.end(moment(raw.runUntil));
            }
        } else {
            throw new Error(`Invalid schedule type: ${raw.type}`)
        }
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
        this.type = type || RecipientType.EMAIL;
        this.address = ko.observable("");
        this.format = ko.observable(ReportFormat.PDF);
    }

    public load(raw): this {
        this.address(raw.address);

        const type: RecipientType = RecipientType[raw.type as string];
        if (type === undefined) {
            throw new Error(`Unknown recipient type: ${raw.type}`)
        }

        const format: ReportFormat = ReportFormat[raw.format.type as string];
        if (format === undefined) {
            throw new Error(`Unknown format type: ${raw.format.type}`)
        }
        this.format(format);
        return this;
    }
}
