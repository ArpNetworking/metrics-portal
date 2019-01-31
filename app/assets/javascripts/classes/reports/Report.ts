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
class Report {
    id: string;
    name: string;

    schedule: Schedule;
    source: ChromeScreenshotSource;

    // recipients

    constructor(id: string, name: string, schedule: Schedule, source: ChromeScreenshotSource) {
        this.id = id;
        this.name = name;
        this.schedule = schedule;
        this.source = source;
    }
}

class Schedule {
    period: Period;
    offset: number;
    runAt: Date;
    runUntil?: Date;

    constructor(period: Period, offset: number, runAt: Date, runUntil?: Date) {
        this.period = period;
        this.offset = offset;
        this.runAt = runAt;
        this.runUntil = runUntil;
    }
}

class ChromeScreenshotSource {
    id: string;
    url: string;
    title: string;
    triggeringEventName: string;
    ignoreCertificateErrors: boolean;

    constructor(id: string, url: string, title: string, triggeringEventName: string, ignoreCertificateErrors: boolean) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.triggeringEventName = triggeringEventName;
        this.ignoreCertificateErrors = ignoreCertificateErrors;
    }
}

class PdfFormat {
    widthInches: number;
    heightInches: number;
}

enum Period {
    HOURLY,
    DAILY,
    WEEKLY,
    MONTHLY,
}

export = Report
