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

class Series {
    //This is really an array of elements of [timestamp, data value]
    data: number[][] = [];
    buffer: number [][] = [];
    label: string = "";
    points: any = { show: true };
    lines: any = { show: true, fill: false, stacked: false, fillOpacity: 1.0, fillColor: null };
    bars: any = { show: false, stacked: false };
    color: string = "black";
    colorSubscription: KnockoutSubscription;
    merger: (x: number, y: number) => number;

    static mergeBySum = function(x: number, y: number): number {
        return x + y;
    };

    static mergeByMin = function(x: number, y: number): number {
        return x < y ? x : y;
    };

    static mergeByMax = function(x: number, y: number): number {
        return x > y ? x : y;
    };

    static defaultPoints() {
        return  { show: true };
    }

    static defaultLines() {
        return { show: true, fill: false, stacked: false, fillOpacity: 1.0, fillColor: null };
    }

    static defaultBars() {
        return { show: false, stacked: false };
    }

    constructor(label: string, color: string, statistic: string) {
        this.color = color;
        this.label = label;
        if (statistic == "sum" || statistic == "count") {
            this.merger = Series.mergeBySum;
        } else if (statistic == "max") {
            this.merger = Series.mergeByMax;
        } else if (statistic == "min") {
            this.merger = Series.mergeByMin;
        } else {
            // Obviously this is very broken for percentiles.
            this.merger = Series.mergeByMax;
        }
    }

    pushData(timestamp: number, dataValue: number, paused: boolean) {
        if (paused) {
            // While paused any values that would merge into data are dropped
            this.pushInternal(this.buffer, timestamp, dataValue);
        } else {
            if (this.buffer.length > 0) {
                this.data = this.data.concat(this.buffer);
                this.buffer = [];
            }
            this.pushInternal(this.data, timestamp, dataValue);
        }
    }

    pushInternal(target: number [][], timestamp: number, dataValue: number) {
        // This only supports in-order merge (for now)
        if (target.length == 0 || target[target.length - 1][0] < timestamp) {
            target.push([timestamp, dataValue]);
        } else if (target[target.length - 1][0] == timestamp) {
            const currentValue = target[target.length - 1][1]
            target[target.length - 1][1] = this.merger(currentValue, dataValue);
        } else {
            console.log("Received out of order data: " + timestamp + " vs" + target[target.length - 1][0] + " for " + this.label);
        }
    }
}

export = Series;
