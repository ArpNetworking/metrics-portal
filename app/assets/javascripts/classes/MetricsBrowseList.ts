/*
 * Copyright 2016 Groupon.com
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

import ko = require('knockout');
import ns = require('./NaturalSort');
import ServiceData = require('./ServiceData');
import ServiceNodeVM = require("./ServiceNodeVM");
import GraphSpec = require("./GraphSpec");
import FolderNodeVM = require("./FolderNodeVM");
import MetricNodeVM = require("./MetricNodeVM");
import StatisticNodeVM = require("./StatisticNodeVM");
import Series = require("./Series");
import NewMetricData = require("./NewMetricData");

class MetricsBrowseList {
    serviceNodes: KnockoutObservableArray<ServiceNodeVM> = ko.observableArray<ServiceNodeVM>();

    public bind(serviceList: ServiceData[]): void {
        var start = Date.now();
        console.debug("starting binding");
        serviceList.forEach((service) => {
            var serviceNodeVM = this.findOrCreateService(service);

            service.children.forEach((metric) => {
                var nameParts = this.getPathParts(metric.name);
                var metricNode = this.addMetric(nameParts, serviceNodeVM);
                metric.children.forEach((statistic) => {
                    this.addStatistic(metricNode, new GraphSpec(service.name, metric.name, statistic.name,
                                                                Series.defaultPoints(), Series.defaultLines(), Series.defaultBars()))
                });
            });
        });
        var end = Date.now();
        console.log("done binding, took " + (end - start) + " millis", this.serviceNodes());
    }

    private findOrCreateService(service: ServiceData): ServiceNodeVM {
        var nodeVM = this.findServiceNode(service.name);
        if (nodeVM == null) {
            nodeVM = new ServiceNodeVM(service.name, this.idify(service.name));
            this.serviceNodes.push(nodeVM);
            this.sortNodeArray(this.serviceNodes);
        }
        return nodeVM;
    }

    public addNewMetric(metric: NewMetricData): void {
        var serviceNodeVM: ServiceNodeVM = this.findOrCreateService({name: metric.service, children: []});
        var nameParts: string[] = this.getPathParts(metric.metric);
        var metricNode = this.addMetric(nameParts, serviceNodeVM);
        this.addStatistic(metricNode, new GraphSpec(metric.service, metric.metric, metric.statistic,
            Series.defaultPoints(), Series.defaultLines(), Series.defaultBars()))
    }

    public search(needle: string) {
        console.debug("starting search for", needle);
        var start = Date.now();
        var regex: RegExp = null;
        if (needle[0] == '/' && needle[needle.length - 1] == '/') {
            // Treat the search term as a regex
            regex = new RegExp(needle.substr(1, needle.length - 2), "i");
        } else if (needle.indexOf("*") >= 0 || needle.indexOf("?") >= 0) {
            // Treat the search term as a wildcard expression:
            // ? = match any one character
            // * = match zero or more characters
            var escapedSearch = needle.replace(/[-\/\\^$+.()|[\]{}]/g, '\\$&');
            regex = new RegExp(escapedSearch.replace("?", ".").replace("*", ".*"), "i");
        } else {
            // Just search for any path containing the search term
            regex = new RegExp(needle.replace(/[-\/\\^$+.()|[\]{}]/g, '\\$&'), "i");
        }
        this.searchNodes(needle, regex, this.serviceNodes, false);
        var end = Date.now();
        console.log("search complete, took " + (end - start) + " millis");
    }

    public searchNodes(searchTerm: string, regex: RegExp, nodes: KnockoutObservableArray<BrowseNode>, forceVisible: boolean): boolean {
        var expandParent = false;

        nodes().forEach((node) => {
            // Not a lot of useful data in statistic nodes
            if (node instanceof StatisticNodeVM) {
                return false;
            }
            var found = regex.test(node.name());
            var forceChildrenVisible = forceVisible || found;
            var foundInChildren = this.searchNodes(searchTerm, regex, node.subfolders, forceChildrenVisible);
            foundInChildren = foundInChildren || this.searchNodes(searchTerm, regex, node.children, forceChildrenVisible);
            if (searchTerm.length == 0) {
                node.expanded(false);
                node.visible(true);
            } else if (foundInChildren) {
                node.expanded(true);
                node.visible(true);
            } else if (found) {
                node.expanded(false);
                node.visible(true);
            } else {
                node.expanded(false);
                node.visible(forceVisible);
            }
            expandParent = expandParent || foundInChildren || found;
        });
        return expandParent;
    }

    private idify(value: string): string {
        if (value == undefined) {
            return undefined;
        }
        value = value.replace(/ /g, "_").toLowerCase();
        return value.replace(/\//g, "_");
    };

    private getPathParts(path: string): string[] {
        return path.split("/");
    };

    private addStatistic(node: BrowseNode, spec: GraphSpec) {
        var existingStatistic = this.findNodeByName(node.children, spec.statistic);
        if (existingStatistic == undefined) {
            node.children.push(new StatisticNodeVM(spec, this.idify(spec.service + "_" + spec.metric + "_" + spec.statistic)));
            this.sortNodeArray(node.children);
        }
    }

    private addMetric(nameParts: string[], node: BrowseNode): BrowseNode {
        // Traverse the tree
        var name = nameParts.shift();
        var nextNode;
        if (nameParts.length > 0) {
            nextNode = this.findNodeByName(node.subfolders, name);
            if (nextNode == undefined) {
                nextNode = new FolderNodeVM(name, this.idify(name));
                node.subfolders.push(nextNode);
                this.sortNodeArray(node.subfolders);
            }
            return this.addMetric(nameParts, nextNode);
        } else {
            nextNode = this.findNodeByName(node.children, name);
            if (nextNode == undefined) {
                nextNode = new MetricNodeVM(name, this.idify(name));
                node.children.push(nextNode);
                this.sortNodeArray(node.children);
            }
            return nextNode;
        }
    }

    private findServiceNode(name: string): ServiceNodeVM {
        return this.binarySearch(this.serviceNodes(), name, (node) => { return node.name(); }, this.stringCompare);
    }

    private findNodeByName<T extends BrowseNode>(haystack: KnockoutObservableArray<T>, needle: string): T {
        return this.binarySearch<T, string>(haystack(), needle, (node) => { return node.name(); }, this.stringCompare);
    }

    private binarySearch<T, U>(haystack: T[], needle: U, nodeMapper: (t: T) => U, comparator: (first: U, second: U) => number): T {
        if (haystack.length == 0) {
            return undefined;
        }
        var min = 0;
        var max = haystack.length - 1;
        var current = Math.floor((max + min) / 2);
        var cmp = comparator(needle, nodeMapper(haystack[current]));
        while (cmp != 0 && min < max) {
            if (cmp < 0) {
                max = current;
            } else {
                min = Math.max(current, min + 1);
            }
            current = Math.floor((max + min) / 2);
            cmp = comparator(needle, nodeMapper(haystack[current]));
        }
        if (cmp == 0) {
            return haystack[current];
        } else {
            return undefined;
        }
    }

    private nodeComparator(first: BrowseNode, second: BrowseNode): number {
        return this.stringCompare(first.name(), second.name());
    }

    private stringCompare(first: string, second: string): number {
        return ns.naturalSort(first, second);
    }

    private sortRecurse(nodes: KnockoutObservableArray<BrowseNode>): void {
        this.sortNodeArray(nodes);
        nodes().forEach((child) => this.sortRecurse(child.children));
    }

    private sortNodeArray(nodes: KnockoutObservableArray<BrowseNode>) : void {
        nodes.sort((a, b) => { return this.nodeComparator(a, b); });
    }
}

export = MetricsBrowseList;
