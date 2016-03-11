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


///<reference path="../libs/flotr2/flotr2.d.ts"/>

import ConnectionVM = require('./ConnectionVM');
import StatisticView = require('./StatisticView');
import Series = require('./Series');
import ViewDuration = require('./ViewDuration');
import GraphSpec = require('./GraphSpec');
import ko = require('knockout');

import Flotr = require('flotr2');

// requestAnimFrame shim with setTimeout fallback
window.requestAnimationFrame = (function () {
    return window.requestAnimationFrame ||
        (<any>window).webkitRequestAnimationFrame ||
        (<any>window).mozRequestAnimationFrame ||
        (<any>window).oRequestAnimationFrame ||
        (<any>window).msRequestAnimationFrame ||
        function (callback) {
            window.setTimeout(callback, 1000 / 60);
        };
})();

class GraphVM implements StatisticView {
    id: string;
    name: string;
    started: boolean = false;
    container: HTMLElement = null;
    data: Series[] = [];
    dataStreams: { [key: string]: number } = {};
    stop: boolean = false;
    paused: boolean = false;
    pauseTime: number = 0;
    targetFrameRate: number = 60;
    duration: number = 30000;
    endAt: number = 0;
    dataLength: number = 600000;
    spec: GraphSpec;
    config: number = 0;
    showConfig: KnockoutObservable<boolean>     = ko.observable<boolean>(false);
    renderDots: KnockoutObservable<boolean>     = ko.observable<boolean>(true);
    renderStacked: KnockoutObservable<boolean>  = ko.observable<boolean>(false);
    graphType: KnockoutObservable<string>       = ko.observable<string>("line");

    constructor(id: string, name: string, spec: GraphSpec) {
        this.id = id;
        this.name = name;
        this.spec = spec;

        this.graphType.subscribe((type: string) => {
            if (type == "scatter") {
                for (var series = 0; series < this.data.length; series++) {
                    this.data[series].points.show   = true;
                    this.data[series].lines.show    = false;
                    this.data[series].lines.fill    = false;
                    this.data[series].lines.stacked = false;
                    this.data[series].bars.show     = false;
                    this.data[series].bars.stacked  = false;
                }
            } else if (type == "line") {
                for (var series = 0; series < this.data.length; series++) {
                    this.data[series].points.show   = false;
                    this.data[series].lines.show    = true;
                    this.data[series].lines.fill    = false;
                    this.data[series].lines.stacked = false;
                    this.data[series].bars.show     = false;
                    this.data[series].bars.stacked  = false;
                }
            } else if (type == "area") {
                for (var series = 0; series < this.data.length; series++) {
                    this.data[series].points.show   = false;
                    this.data[series].lines.show    = true;
                    this.data[series].lines.fill    = true;
                    this.data[series].lines.stacked = false;
                    this.data[series].bars.show     = false;
                    this.data[series].bars.stacked  = false;
                }
            } else if (type == "bar") {
                for (var series = 0; series < this.data.length; series++) {
                    this.data[series].points.show   = false;
                    this.data[series].lines.show    = false;
                    this.data[series].lines.fill    = false;
                    this.data[series].lines.stacked = false;
                    this.data[series].bars.show     = true;
                    this.data[series].bars.stacked  = false;
                }
            }

            this.renderDots(false);
            this.renderStacked(false);
        });

        this.renderDots.subscribe((state: boolean) => {
            if (this.graphType() != "line") { return; }

            for (var series = 0; series < this.data.length; series++) {
                if (this.data[series].lines) {
                    this.data[series].points.show = state;
                }
            }
        });

        this.renderStacked.subscribe((state: boolean) => {
            if (this.graphType() != "bar") { return; }

            for (var series = 0; series < this.data.length; series++) {
                if (this.data[series].bars) {
                    this.data[series].bars.stacked = state;
                }
            }
        });
    }

    disconnectConnection(cvm: ConnectionVM) {
        var index = this.dataStreams[cvm.server];
        if (index != undefined) {
            delete this.dataStreams[cvm.server];
            var series = this.data.splice(index, 1);
            series[0].colorSubscription.dispose();

            //we now need to re-index dataStreams
            //any element with an index > the var index needs to be decremented
            for (var i in this.dataStreams) {
                var old = this.dataStreams[i];
                if (old > index) {
                    this.dataStreams[i] = old - 1;
                }
            }
        }
    }

    shutdown() {
        this.stop = true;
    }

    setViewDuration(window: ViewDuration) {
        var endTime = this.dataLength - window.end;
        this.duration = window.end - window.start;
        this.endAt = endTime;
    }

    niceName(id: string): string {
        return id.replace(/:/g, " ");
    }

    updateColor(cvm: ConnectionVM): void {
        var index = this.dataStreams[cvm.server];
        this.data[index].color = cvm.color();
        this.data[index].points.fillOpacity = cvm.alpha();
    }

    postData(server: string, timestamp: number, dataValue: number, cvm: ConnectionVM) {
        var index = this.dataStreams[cvm.server];
        if (index == undefined) {
            index = this.data.length;
            this.dataStreams[cvm.server] = index;
            var series = new Series(cvm.server, cvm.color());
            series.colorSubscription = cvm.color.subscribe((color: String) => {
                this.updateColor(cvm);
            });
            this.data.push(series);
        }

        this.data[index].pushData(timestamp, dataValue, this.paused);
    }

    setPause(pause: boolean) {
        this.paused = pause;
        this.pauseTime = new Date().getTime() - 1000;
    }

    configGraph() {
        this.showConfig(!this.showConfig());
    }

    render() {
        if (this.stop || this.container.clientWidth == 0) {
            return;
        }

        //set min and max
        var graphMin = 1000000000;
        var graphMax = -1000000000;
        var maxValues = {};

        var now = new Date().getTime() - 1000;
        if (this.paused) {
            now = this.pauseTime
        }

        var graphEnd = now - this.endAt;
        var graphStart = graphEnd - this.duration;
        for (var series = 0; series < this.data.length; series++) {
            //Deleted entries are not removed from the array in order to
            //preserve the hash of name => index
            if (this.data[series] === undefined) {
                continue;
            }

            if (this.data[series].data.length <= 0) {
                continue;
            }

            //shift the data off the array that is too old
            if (!this.paused) {
                while (this.data[series].data[1] != undefined && this.data[series].data[1][0] < graphEnd - this.dataLength) {
                    this.data[series].data.shift();
                }
            }

            //find the indexes in the window
            var lower = this.data[series].data.length;
            var upper = 0;
            for (var iter = this.data[series].data.length - 1; iter >= 0; iter--) {
                var dataTimestamp = this.data[series].data[iter][0];
                if (dataTimestamp >= graphStart && dataTimestamp <= graphEnd) {
                    if (iter < lower) {
                        lower = iter;
                    }

                    if (iter > upper) {
                        upper = iter;
                    }
                }
            }

            if (lower > 0) {
                lower--;
            }

            if (upper < this.data[series].data.length - 1) {
                upper++;
            }

            for (var back = lower; back <= upper; back++) {
                //it's in our view window
                var dataVal = this.data[series].data[back][1];
                if (dataVal > graphMax) {
                    graphMax = dataVal;
                }

                if (dataVal < graphMin) {
                    graphMin = dataVal;
                }

                if (maxValues[back] != null) {
                    maxValues[back] += dataVal;
                } else {
                    maxValues[back] = dataVal;
                }
            }
        }

        if (this.data.length > 0) {
            if ((this.data[0].bars.show && this.data[0].bars.stacked) ||
                (this.data[0].lines.show && this.data[0].lines.stacked)) {
                graphMax = -1000000000;
                Object.keys(maxValues).forEach((key) => {
                    if (graphMax < maxValues[key]) {
                        graphMax = maxValues[key];
                    }
                });
            }
        }

        if (graphMax == graphMin) {
            graphMin--;
            graphMax++;
        } else if (graphMin > graphMax) {
            //This is the case with no data
            graphMin = 0;
            graphMax = 1;
        } else {
            var spread = graphMax - graphMin;
            var buffer = spread / 10;
            graphMin -= buffer;
            graphMax += buffer;
        }

        // Draw Graph
        Flotr.draw(this.container, this.data, {
            yaxis: {
                max: graphMax,
                min: graphMin
            },
            xaxis: {
                mode: 'time',
                noTicks: 3,
                min: graphStart,
                max: graphEnd,
                timeMode: "local"

            },
            title: this.name,
            HtmlText: false,
            mouse: {
                track: false,
                sensibility: 8,
                radius: 15
            },
            legend: {
                show: false
            }
        });
    }

    start(paused: boolean) {
        if (this.started == true) {
            return;
        }

        this.paused = paused;
        this.started = true;
        this.container = document.getElementById(this.id);
    }
}

export = GraphVM;
