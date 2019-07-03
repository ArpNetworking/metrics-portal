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

import {
    BaseRecipientViewModel,
    BaseScheduleViewModel,
    BaseSourceViewModel,
    ReportFormat,
    ScheduleRepetition,
    SourceType,
} from "./Models";

export default class Report {
    id: string;
    name: string;

    schedule: ScheduleViewModel;
    source: SourceViewModel;
    recipients: RecipientViewModel[];

    editUri: string;

    constructor(id: string, name: string, source: any, schedule: any, recipients: object[]) {
        this.id = id;
        this.name = name;

        this.recipients = recipients.map((raw) =>
            new RecipientViewModel().load(raw)
        );
        this.schedule = new ScheduleViewModel().load(schedule);
        this.source = new SourceViewModel().load(source);

        this.editUri = `#report/edit/${this.id}`;
    }
}

class RecipientViewModel extends BaseRecipientViewModel {
    badgeText: KnockoutComputed<string>;

    constructor() {
        super();
        this.badgeText = ko.computed(() =>
            `${this.address()} (${ReportFormat[this.format()].toUpperCase()})`
        );
    }
}

class SourceViewModel extends BaseSourceViewModel {
    displayText: KnockoutComputed<string>;

    constructor() {
        super();
        this.displayText = ko.computed<string>(() => {
            const type = "Browser rendered";
            return `${this.title()} (${type})`
        });
    }

}

class ScheduleViewModel extends BaseScheduleViewModel {
    static readonly DatetimeFormat = "llll";

    // Unlike EditScheduleViewModel, we use a more readable format for displaying datetimes, since
    // this value does not need to be parsed (unlike in an editable form field).
    startText: KnockoutComputed<string> =
        ko.pureComputed(() => this.start().format(ScheduleViewModel.DatetimeFormat));
    endText: KnockoutComputed<string> = ko.pureComputed(() => {
        const end = this.end();
        if (!end) {
            return "–";
        }
        return end.format(ScheduleViewModel.DatetimeFormat);
    });
    displayType: KnockoutComputed<string> = ko.pureComputed(() => {
        switch (this.repeat()) {
            case ScheduleRepetition.ONE_OFF:
                return "One-off";
            case ScheduleRepetition.DAILY:
                return "Daily";
            case ScheduleRepetition.HOURLY:
                return "Hourly";
            case ScheduleRepetition.MONTHLY:
                return "Monthly";
            case ScheduleRepetition.WEEKLY:
                return "Weekly";
        }
    });
}
