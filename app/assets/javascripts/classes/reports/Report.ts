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

interface OneOffSchedule {
    type: 'OneOff';
    runAtAndAfter: string;
    runUntil: string;
    zone: string;
}

interface PeriodicSchedule {
    type: 'Periodic';
    period: string;
    runAtAndAfter: string;
    runUntil: string;
    zone: string;
    offset?: string;
}

type Schedule = OneOffSchedule | PeriodicSchedule;

export default class Report {
    id: string;
    name: string;
    editUri: string;
    schedule: any;

    // schedule: Schedule;
    // source: ChromeScreenshotSource;
    // recipients: [Recipient];

    // recipients
    constructor(id: string, name: string, schedule: any) {
        this.id = id;
        this.name = name;
        this.editUri = `#report/edit/${this.id}`;
        this.schedule = schedule;
        // this.source = source;
    }
}