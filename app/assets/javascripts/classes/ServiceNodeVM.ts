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

import MetricNodeVM from './MetricNodeVM';
import * as ko from 'knockout';
import FolderNodeVM from './FolderNodeVM';
import BrowseNode from './BrowseNode';

class ServiceNodeVM implements BrowseNode {
    name: ko.Observable<string>;
    children: ko.ObservableArray<MetricNodeVM>;
    expanded: ko.Observable<boolean>;
    renderAs: ko.Observable<string>;
    subfolders: ko.ObservableArray<FolderNodeVM>;
    visible: ko.Observable<boolean>;

    constructor(name: string, id: string) {
        this.name = ko.observable(name);
        this.children = ko.observableArray<MetricNodeVM>();
        this.subfolders = ko.observableArray<FolderNodeVM>();
        this.expanded = ko.observable(false);
        this.renderAs = ko.observable("service_node");
        this.visible = ko.observable(true);
    }

    expandMe() {
        this.expanded(this.expanded() == false);
    }
}

export default ServiceNodeVM;
