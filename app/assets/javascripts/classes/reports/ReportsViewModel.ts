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

import PaginatedSearchableList = require("../PaginatedSearchableList");
import Report from "./Report";
import csrf from '../Csrf'

class ReportsList extends PaginatedSearchableList<Report> {
    constructor() {
        super("report");
    }

    fetchData(query: any, callback) {
        $.getJSON("v1/reports/query", query, (page) => {
            const reportList: Report[] = page.data.map((rawReport)=> {
                return new Report(
                    rawReport.id,
                    rawReport.name,
                    rawReport.source,
                    rawReport.schedule,
                    rawReport.timeout,
                    rawReport.recipients,
                )
            });
            callback(reportList, page.pagination);
        })
    }

}

class ReportsViewModel {
    reports: ReportsList = new ReportsList();
    deletingId: string = null;

    constructor() {
        this.reports.query();
    };

    remove: (Report) => void = (report: Report) => {
        this.deletingId = report.id;
        $("#confirm-delete-modal").modal('show');
    };

    confirmDelete: () => void = () => {
        $.ajax({
            type: "DELETE",
            url: "/v1/reports/" + this.deletingId,
            beforeSend: function(request) {
                request.setRequestHeader("Csrf-Token", csrf.getToken());
            },
            contentType: "application/json"
        }).done(() => {
            $("#confirm-delete-modal").modal('hide');
            this.reports.query();
            this.deletingId = null;
        });
    }

}

export = ReportsViewModel;
