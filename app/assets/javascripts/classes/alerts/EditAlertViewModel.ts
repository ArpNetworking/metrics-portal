/*
 * Copyright 2017 Smartsheet.com
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

import AlertData = require('./AlertData');
import ko = require('knockout');
import $ = require('jquery');
import uuid = require('../Uuid');
import csrf from '../Csrf';
import * as moment from 'moment/moment';
import * as d3 from 'd3';
import jqXHR = JQuery.jqXHR;

class OperatorOption {
    text: string;
    value: string;

    constructor(text, value) {
        this.text = text;
        this.value = value;
    }
}

class MetricsResponse {
    response: QueryResponse;
    errors: string[];
    warnings: string[];
}

class QueryResponse {
    queries: Query[];
}

class Query {
    results: Results[];
}
type Datapoint = [number, number];

class Results {
    values: Datapoint[];
    alerts: AlertTrigger[];
}

class Series {
    id: string;
    values: Datapoint[];
}

class RangeDatapoint {
    constructor(timestamp: number, min: number, max: number) {
        this.timestamp = timestamp;
        this.min = min;
        this.max = max;
    }

    timestamp: number;
    min: number;
    max: number;
}

class AlertTrigger {
    time: string;
    endTime: string;
}

class RangeSeries {
    id: string;
    values: RangeDatapoint[] = [];
}

class EditAlertViewModel {
    id = ko.observable<string>("");
    name = ko.observable<string>("");
    query = ko.observable<string>("select my_metric group by cluster | avg | threshold threshold=10 operator=GREATER_THAN").extend({ rateLimit: { timeout: 500, method: "notifyWhenChangesStop" } });
    period = ko.observable<string>("PT1M");
    container: HTMLElement;
    queryErrors = ko.observableArray<string>();
    queryWarnings = ko.observableArray<string>();
    dateRange = ko.observable<any[]>([moment().subtract(2, 'hours'), moment()]);
    formattedDateRange = ko.computed(() => {
        let range = this.dateRange();
        let start = range[0];
        let end = range[1];
        return start.calendar() + " to " + end.calendar();
    });

    formattedQuery = ko.computed(() => {
        let dateRange = this.dateRange();
        let startTime = dateRange[0];
        let endTime = dateRange[1];
        return "from '" + startTime.toISOString() + "' to '" + endTime.toISOString() + "' " + this.query();
    });

    constructor() {
        this.formattedQuery.subscribe((newValue) => this.queryChanged(newValue));
    }

    activate(id: string) {
        if (id != null) {
            this.loadAlert(id);
        } else {
            this.id(uuid.v4());
        }
    }
    compositionComplete() {
        this.container = document.getElementById('graph');
    }

    loadAlert(id: string): void {
        $.getJSON("/v1/alerts/" + id, {}, (data: AlertData) => {
            this.id(data.id);
            this.name(data.name);
            this.query(data.query);
            this.period(data.period);
        });
    }

    queryChanged(newValue: string): void {
        this.executeQuery(newValue);
    }

    private executeQuery(query: string): any {
        $.ajax({
            type: 'POST',
            url: '/v1/metrics/query',
            beforeSend: function(request) {
                request.setRequestHeader("Csrf-Token", csrf.getToken());
            },
            contentType: "application/json",
            dataType: "json",
            data: JSON.stringify({'query': query}),
            success: (response) => this.queryDataLoad(response),
            error: (request, status, error) => this.queryFailed(request)
        });
    }

    private queryFailed(request: jqXHR) {
        this.queryErrors.removeAll();
        this.queryWarnings.removeAll();
        if (request.status / 100 == 4) {
            if (request.responseJSON != null && request.responseJSON.errors != null && request.responseJSON.errors.length > 0) {
                this.queryErrors.push(request.responseJSON.errors[0]);
            }
        } else if (request.status / 100 == 5) {
            this.queryErrors.push("An unknown error has occurred, please try again later");
        }
    }

    private queryDataLoad(response: MetricsResponse) {
        this.queryErrors.removeAll();
        this.queryWarnings.removeAll();
        let series: Series[] = [];
        let rangeSeriesList: RangeSeries[] = [];
        let alertData: AlertTrigger[] = [];
        let i = 0;
        response.warnings.forEach(w => this.queryWarnings.push(w));
        response.errors.forEach(e => this.queryErrors.push(e));
        response.response.queries.forEach((query) => {
            query.results.forEach((result) => {
                alertData = alertData.concat(result.alerts);
                //TODO: walk the values to look for duplicates, if duplicates create a RangeSeries from it
                let values = result.values;
                let last = null;
                let range = false;
                for (let j = 0; j < values.length; j++) {
                    if (values[j][0] == last) {
                        range = true;
                        break;
                    }
                    last = values[j][0];
                }
                if (!range) {
                    series.push({values: result.values, id: String(i++)});
                } else {
                    this.queryWarnings.push("Query has a series with multiple values for a given time.");

                    last = null;

                    let rangeSeries: RangeSeries = new RangeSeries();
                    rangeSeries.id = String(i++);
                    let rangeIndex = -1;
                    for (let j = 0; j < values.length; j++) {
                        if (values[j][0] != last) {
                            rangeSeries.values.push(new RangeDatapoint(values[j][0], values[j][1], values[j][1]));
                            rangeIndex++;
                        } else {
                            if (rangeSeries.values[rangeIndex].max < values[j][1]) {
                                rangeSeries.values[rangeIndex].max = values[j][1];
                            }
                            if (rangeSeries.values[rangeIndex].min > values[j][1]) {
                                rangeSeries.values[rangeIndex].min = values[j][1];
                            }
                        }
                        last = values[j][0];
                    }

                    rangeSeriesList.push(rangeSeries);
                }
            });
        });

        let svg = d3.select(this.container);
        svg.select("g").remove();
        let margin = {top: 20, right: 20, bottom: 30, left: 30};
        let width = 0;
        let height = 0;
        if (svg.node() != null) {
            width = svg.node().getBoundingClientRect().width - margin.left - margin.right;
            height = svg.node().getBoundingClientRect().height - margin.top - margin.bottom;
        }
        let g = svg.append("g").attr("transform", "translate(" + margin.left + "," + margin.top + ")");
        let x = d3.scaleTime()
            .rangeRound([0, width]);

        let y = d3.scaleLinear()
            .rangeRound([height, 0]);
        let z = d3.scaleOrdinal(d3.schemeCategory10);

        let line = d3.line<Datapoint>()
            .x((d) => { return x(d[0]); })
            .y((d) => { return y(d[1]); });

        let xrange = [
            d3.min([
                d3.min(series, ts => d3.min(ts.values, d => d[0])),
                d3.min(rangeSeriesList, rs => d3.min(rs.values, d => d.timestamp)),
                +this.dateRange()[0]]),
            d3.max([
                d3.max(series, ts => d3.max(ts.values, d => d[0])),
                d3.max(rangeSeriesList, rs => d3.max(rs.values, d => d.timestamp)),
                +this.dateRange()[1]])];
        x.domain(xrange);
        let yrange = [
            d3.min([
                d3.min(series, ts => d3.min(ts.values, d => d[1])),
                d3.min(rangeSeriesList, rs => d3.min(rs.values, d => d.min))]),
            d3.max([
                d3.max(series, ts => d3.max(ts.values, d => d[1])),
                d3.max(rangeSeriesList, rs => d3.max(rs.values, d => d.max))])];
        // let yrange = [d3.min(series[0], function(d) { return d[1]; }), d3.max(series[0], function(d) { return d[1]; })];
        if (isNaN(yrange[0])) {
            yrange[0] = 0;
        }
        if (isNaN(yrange[1])) {
            yrange[1] = 1;
        }
        if (yrange[0] === yrange[1]) {
            yrange[0]--;
            yrange[1]++;
        }
        y.domain(yrange);

        let area = d3.area<RangeDatapoint>()
            .x((d) => { return x(d.timestamp); })
            .y0((d) => { return y(d.min); })
            .y1((d) => { return y(d.max); });

        let alertRect = d3.area<AlertTrigger>().x0(t => x(moment(t.time).valueOf())).x1(t => x(moment(t.endTime).valueOf() + 1)).y0(y.range()[0]).y1(y.range()[1]);
        g.append("g")
            .attr("transform", "translate(0," + height + ")")
            .call(d3.axisBottom(x));

        g.append("g")
            .call(d3.axisLeft(y))
            .append("text");

        let alert = g.selectAll(".alerts")
            .data(alertData)
            .enter().append("g")
            .attr("class", "alerts");
        alert.append("rect")
            .attr("fill-opacity", "0.1")
            .attr("x", t => x(moment(t.time).valueOf()))
            .attr("y", 0)
            .attr("height", y.range()[0])
            .attr("width", t => x(moment(t.endTime).valueOf()) - x(moment(t.time).valueOf()))
            .attr("fill", "red");

        let ts = g.selectAll(".ts")
            .data(series)
            .enter().append("g")
            .attr("class", "ts");

        ts.append("path")
            .attr("class", "line")
            .attr("fill", "none")
            .attr("d", function(d) { return line(d.values); })
            .style("stroke", function(d) { return z(d.id); });

        let ats = g.selectAll(".ats")
            .data(rangeSeriesList)
            .enter().append("g")
            .attr("class", "ats");
        ats.append("path")
            .attr("class", "area")
            .attr("fill", function(d) { return z(d.id); })
            .attr("d", function(d) { return area(d.values); })
            .style("opacity", 0.3)
            .style("stroke", function(d) {return z(d.id); });

    }

    save(): void {
        $.ajax({
            type: "PUT",
            url: "/v1/alerts",
            beforeSend: function(request) {
                request.setRequestHeader("Csrf-Token", csrf.getToken());
            },
            contentType: "application/json",
            dataType: "json",
            data: JSON.stringify({
                "id": this.id(),
                "query": this.query(),
                "name": this.name(),
                "period": this.period()
            }),
        }).done(() => {
            window.location.href = "/#alerts";
        });
    }

    cancel(): void {
        window.location.href = "/#alerts";
    }
}

export = EditAlertViewModel;
