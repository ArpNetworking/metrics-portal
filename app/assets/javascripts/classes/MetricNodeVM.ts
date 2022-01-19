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

import GraphViewModel from './GraphViewModel';
import StatisticNodeVM from './StatisticNodeVM';
import * as ko from 'knockout';
import BrowseNode from "./BrowseNode";

class MetricNodeVM implements BrowseNode {
    metricName: ko.Observable<string>;
    name: ko.Observable<string>;
    children: ko.ObservableArray<BrowseNode>;
    subfolders: ko.ObservableArray<BrowseNode>;
    expanded: ko.Observable<boolean>;
    renderAs: ko.Observable<string>;
    visible: ko.Observable<boolean>;

    constructor(metricName: string, displayName: string, id: string) {
        this.metricName = ko.observable(metricName);
        this.name = ko.observable(displayName);
        this.children = ko.observableArray<BrowseNode>();
        this.subfolders = ko.observableArray<BrowseNode>();
        this.expanded = ko.observable(false);
        this.renderAs = ko.observable("metric_node");
        this.visible = ko.observable(true);
    }

    expandMe() {
        this.expanded(this.expanded() == false);
    }
}

export default MetricNodeVM;
