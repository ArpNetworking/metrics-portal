/*
 * Copyright 2015 Groupon.com
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
import PaginatedSearchableList = require('../PaginatedSearchableList');
import $ = require('jquery');
import csrf from '../Csrf'

class AlertsList extends PaginatedSearchableList<AlertData> {
    fetchData(query, callback) {
        $.getJSON("v1/alerts/query", query, (data) => {
            const alertsList: AlertData[] = data.data.map((v: AlertData)=> { return new AlertData(
                v.id,
                v.name,
                v.period,
                v.extensions
            );});
            callback(alertsList, data.pagination);
        });
    }
}

class AlertsViewModel {
    alerts: AlertsList = new AlertsList();
    deletingId: string = null;
    remove: (alert: AlertData) => void;

    constructor() {
        this.alerts.query();
        this.remove = (alert: AlertData) => {
            this.deletingId = alert.id;
            console.log("set deletingId: ", this, this.deletingId);

            $("#confirm-delete-modal").modal('show');
        };
    }

    confirmDelete() {
        $.ajax({
            type: "DELETE",
            url: "/v1/alerts/" + this.deletingId,
            beforeSend: function(request) {
                request.setRequestHeader("Csrf-Token", csrf.getToken());
            },
            contentType: "application/json"
        }).done(() => {
            $("#confirm-delete-modal").modal('hide');
            this.alerts.query();
            this.deletingId = null;
        });
    }
}

export = AlertsViewModel;
