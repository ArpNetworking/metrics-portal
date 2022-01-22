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

import * as ko from 'knockout';
import BrowseNode from "./BrowseNode";

class FolderNodeVM implements BrowseNode {
    name: ko.Observable<string>;
    subfolders: ko.ObservableArray<BrowseNode>;
    children: ko.ObservableArray<BrowseNode>;
    expanded: ko.Observable<boolean>;
    renderAs: ko.Observable<string>;
    icon: ko.Computed<string>;
    visible: ko.Observable<boolean>;

    constructor(name: string, id: string) {
        this.name = ko.observable(name);
        this.subfolders = ko.observableArray<BrowseNode>();
        this.children = ko.observableArray<BrowseNode>();
        this.expanded = ko.observable(false);
        this.renderAs = ko.observable("folder_node");
        this.icon = ko.pureComputed<string>(() => { return this.cssIcon(); }).extend({ deferred: true});
        this.visible = ko.observable(true);
    }

    cssIcon(): string {
        return this.expanded() ? "glyphicon-folder-open" : "glyphicon-folder-close";
    }
    expandMe() {
        this.expanded(this.expanded() == false);
    }
}

export default FolderNodeVM
