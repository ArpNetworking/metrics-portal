import uuid from '../Uuid';
import * as ko from 'knockout';
// @ts-ignore: import is valid
import * as moment from 'moment-timezone/moment-timezone';
import features from '../../libs/configure';

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

export const availableSourceTypes: SourceType[] = features.reportingSourceTypes.map((s: keyof typeof SourceType) => SourceType[s]);
export const availableReportFormats: ReportFormat[] = features.reportingReportFormats.map((s: keyof typeof ReportFormat) => ReportFormat[s]);
export const availableRecipientTypes: RecipientType[] = features.reportingRecipientTypes.map((s: keyof typeof RecipientType) => RecipientType[s]);
export const availableReportIntervals: ScheduleRepetition[] = features.reportingIntervals.map((s: keyof typeof ScheduleRepetition) => ScheduleRepetition[s]);

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
    type = ko.observable<SourceType>(availableSourceTypes[0]);
    title = ko.observable<string>("");
    url = ko.observable<string>("");
    ignoreCertificateErrors = ko.observable<boolean>(false);

    public load(raw): this {
        this.id(raw.id);

        const type = SourceType[raw.type as string];
        if (type === undefined) {
            throw new Error(`Unknown source type: ${raw.type}`);
        }
        this.type(type);

        switch (type) {
            case SourceType.WEB_PAGE:
                this.url(raw.uri);
                this.title(raw.title);
                this.ignoreCertificateErrors(raw.ignoreCertificateErrors);
                break;
            case SourceType.GRAFANA:
                this.url(raw.webPageReportSource.uri);
                this.title(raw.webPageReportSource.title);
                this.ignoreCertificateErrors(raw.webPageReportSource.ignoreCertificateErrors);
                break;
        }
        return this;
    }
}

export class BaseScheduleViewModel {
    repeat = ko.observable<ScheduleRepetition>(ScheduleRepetition.ONE_OFF);
    start = ko.observable<moment.Moment>();
    end = ko.observable<moment.Moment | undefined>(undefined);
    offset = ko.pureComputed<moment.Duration>(() => moment.duration(this.offsetString()));
    zone = ko.observable<ZoneInfo>(new ZoneInfo(moment.tz.guess()));

    offsetString = ko.observable<string>("PT10M");

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
    id: ko.Observable<string>;
    type: RecipientType;
    address: ko.Observable<string>;
    format: ko.Observable<ReportFormat>;

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
