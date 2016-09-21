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

import HostData = require('./HostData');
import PaginatedSearchableList = require('../PaginatedSearchableList');
import Hosts = require('../Hosts');
import MetricsSoftwareState = require('./MetricsSoftwareState');
import GraphViewModel = require('../GraphViewModel');
import $ = require('jquery');
import ko = require('knockout');

class HostRegistryViewModel extends PaginatedSearchableList<HostData> {
    versionFilter: KnockoutObservable <string> = ko.observable('');

    constructor() {
        super();
        this.versionFilter.subscribe(() => {this.page(1); this.query()});
        this.query();
    };

    click(connectTo: HostData) {
        Hosts.connectToServer(connectTo.hostname);
    }

    amendQuery(query) {
        var host = this.searchExpressionThrottled();
        var version = this.versionFilter();
        if (host && host != "") {
            query.name = host;
        }
        if (version && version != "") {
            query.state = version;
        }
    }

    fetchData(query, callback) {
        $.getJSON("/v1/hosts/query", query, (data) => {
            var hostList: HostData[] = data.data.map((v: HostData)=> {
                return new HostData(v.hostname, v.metricsSoftwareState);});
            callback(hostList, data.pagination);
        });
    }
}

export = HostRegistryViewModel;
