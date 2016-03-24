/*
 * Copyright 2014 Groupon.com
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

class ReportData {
    data: number;
    metric: string;
    server: string;
    service: string;
    statistic: string;
    timestamp: number;
    points: any = { show: true };
    lines: any = { show: true, fill: false, stacked: false, fillOpacity: 1.0, fillColor: null };
    bars: any = { show: false, stacked: false };

    constructor(data: any) {
        this.data = data.data;
        this.metric = data.metric;
        this.server = data.server;
        this.service = data.service;
        this.statistic = data.statistic;
        this.timestamp = data.timestamp;

        this.points.show = data.points.show;
        this.lines.show = data.lines.show;
        this.lines.fill = data.lines.fill;
        this.lines.stacked = data.lines.stacked;
        this.bars.show = data.bars.show;
        this.bars.stacked = data.bars.stacked;
    }
}

export = ReportData;
