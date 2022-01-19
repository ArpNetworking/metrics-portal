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

import AlertData from './AlertData';
import * as ko from 'knockout';
import * as $ from 'jquery';
import Operator from './Operator';
import Quantity from '../Quantity';
import uuid from '../Uuid';
import csrf from '../Csrf';

class OperatorOption {
    text: string;
    value: string;

    constructor(text, value) {
        this.text = text;
        this.value = value;
    }
}

class ContextOption {
    text: string;
    value: string;

    constructor(text, value) {
        this.text = text;
        this.value = value;
    }
}

class EditAlertViewModel {
    id = ko.observable<string>("");
    context = ko.observable<string>("CLUSTER");
    name = ko.observable<string>("");
    metric = ko.observable<string>("");
    service = ko.observable<string>("");
    cluster = ko.observable<string>("");
    statistic = ko.observable<string>("");
    period = ko.observable<string>("PT1M");
    operator = ko.observable<string>("GREATER_THAN");
    value = ko.observable<number>(0);
    valueUnit = ko.observable<string>(null);
    operators = [
        new OperatorOption("<", "LESS_THAN"),
        new OperatorOption("<=", "LESS_THAN_OR_EQUAL_TO"),
        new OperatorOption(">", "GREATER_THAN"),
        new OperatorOption(">=", "GREATER_THAN_OR_EQUAL_TO"),
        new OperatorOption("=", "EQUAL_TO"),
        new OperatorOption("!=", "NOT_EQUAL_TO"),
    ];
    contexts = [
        new ContextOption("Host", "HOST"),
        new ContextOption("Cluster", "CLUSTER")
    ];

    activate(id: String) {
        if (id != null) {
            this.loadAlert(id);
        } else {
            this.id(uuid.v4());
        }
    }

    loadAlert(id: String): void {
        $.getJSON("/v1/alerts/" + id, {}, (data) => {
            console.log(data);
            this.id(data.id);
            this.context(data.context);
            this.name(data.name);
            this.metric(data.metric);
            this.service(data.service);
            this.cluster(data.cluster);
            this.statistic(data.statistic);
            this.period(data.period);
            this.operator(data.operator);
            this.value(data.value.value);
            this.valueUnit(data.value.unit);
        });
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
                "context": this.context(),
                "metric": this.metric(),
                "name": this.name(),
                "cluster": this.cluster(),
                "service": this.service(),
                "statistic": this.statistic(),
                "period": this.period(),
                "operator": this.operator(),
                "value": {"value": this.value(), "unit": this.valueUnit()}
            }),
        }).done(() => {
            window.location.href = "/#alerts";
        });
    }

    cancel(): void {
        window.location.href = "/#alerts";
    }
}

export default EditAlertViewModel;
