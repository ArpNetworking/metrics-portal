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
/// <reference path="../GraphViewModel"/>

import BaseProtocol = require("./BaseProtocol");
import GraphViewModel = require("../GraphViewModel");
import ConnectionModel = require("../ConnectionModel");
import ConnectionVM = require("../ConnectionVM");
import Command = require("../Command");
import MetricsListData = require("../MetricsListData");
import NewMetricData = require("../NewMetricData");
import ReportData = require("../ReportData");
import GraphSpec = require("../GraphSpec");
import MetricsBrowseList = require("../MetricsBrowseList");

declare var require;
class V1Protocol extends BaseProtocol {
    private graphViewModel: any;
    private metricsList: MetricsBrowseList;

    public constructor(cm: ConnectionModel) {
        super(cm);
        this.graphViewModel = require('../GraphViewModel');
        this.metricsList = this.graphViewModel.metricsList;
    }

    public processMessage(data: any, cvm: ConnectionVM) {
        if (data.command == "metricsList") {
            var mlCommand: Command<MetricsListData> = data;
            this.metricsList.bind(mlCommand.data.metrics);
        }
        else if (data.command == "newMetric") {
            var nmCommand: Command<NewMetricData> = data;
            this.metricsList.addNewMetric(nmCommand.data);
        }
        else if (data.command == "report") {
            var rdCommand: Command<ReportData> = data;
            this.graphViewModel.reportData(rdCommand.data, cvm);
        }
        else if (data.response == "ok") {
            this.connectionModel.onHeartbeat();
        }
        else {
            console.warn("unhandled message: ");
            console.warn(data);
        }
    }

    public subscribeToMetric(spec: GraphSpec): void {
        this.send({ command: "subscribe", service: spec.service, metric: spec.metric, statistic: spec.statistic });
    }

    public unsubscribeFromMetric(spec: GraphSpec): void {
        this.send({ command: "unsubscribe", service: spec.service, metric: spec.metric, statistic: spec.statistic });
    }

    public connectionInitialized(): void {
        this.send({ command: "getMetrics" });
    }

    public heartbeat(): void {
        this.send({ command: "heartbeat" });
    }

    public getLogs(): void { }

    public subscribeLog(log: string, regexes: string[]): void { }

    public unsubscribeLog(log: string, regexes: string[]): void { }
}

export = V1Protocol;
