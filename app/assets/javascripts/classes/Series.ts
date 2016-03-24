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

///<reference path="../libs/knockout/knockout.d.ts"/>
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

    static defaultPoints() {
        return  { show: true };
    }

    static defaultLines() {
        return { show: true, fill: false, stacked: false, fillOpacity: 1.0, fillColor: null };
    }

    static defaultBars() {
        return { show: false, stacked: false };
    }

    constructor(label: string, color: string) {
        this.color = color;
        this.label = label;
    }

    pushData(timestamp: number, dataValue: number, paused: boolean) {
        if (this.data.length == 0 || this.data[this.data.length - 1][0] < timestamp) {
            if (paused) {
                this.buffer.push([timestamp, dataValue]);
            } else {
                if (this.buffer.length > 0) {
                    this.data = this.data.concat(this.buffer);
                    this.buffer = [];
                }

                this.data.push([timestamp, dataValue]);
            }
        }
    }
}

export = Series;
