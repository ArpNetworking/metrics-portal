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

///<reference path="../libs/jqueryui/jqueryui.d.ts"/>
///<reference path="../libs/bootstrap/bootstrap.d.ts"/>
///<reference path="../libs/naturalSort/naturalSort.d.ts" />
///<reference path="BrowseNode.ts"/>
///<reference path="ViewModel.ts"/>
///<amd-dependency path="jquery.ui"/>
import app = require('durandal/app');
import MetricData = require('./MetricData');
import MetricNodeVM = require('./MetricNodeVM');
import Color = require('./Color');
import Command = require('./Command');
import GaugeVM = require('./GaugeVM');
import GraphVM = require('./GraphVM');
import StatisticView = require('./StatisticView');
import ServiceNodeVM = require('./ServiceNodeVM');
import StatisticNodeVM = require('./StatisticNodeVM');
import ServiceData = require('./ServiceData');
import StatisticData = require('./StatisticData');
import FolderNodeVM = require('./FolderNodeVM');
import ViewDuration = require('./ViewDuration');
import MetricsListData = require('./MetricsListData');
import NewMetricData = require('./NewMetricData');
import ReportData = require('./ReportData');
import ko = require('knockout');
import kob = require('./KnockoutBindings')
import $ = require('jquery');
import GraphSpec = require('./GraphSpec');
import Hosts = require('./Hosts');
import ConnectionVM = require('./ConnectionVM')
import ns = require('naturalSort');
import MetricsBrowseList = require("./MetricsBrowseList");

module GraphViewModel {
    console.log("defining graphviewmodel");
    export var connections = Hosts.connections;
    export var graphs: KnockoutObservableArray<StatisticView> = ko.observableArray<StatisticView>();
    export var graphsById: { [id: string]: StatisticView } = {};
    export var subscriptions: GraphSpec[] = [];
    export var viewDuration: ViewDuration = new ViewDuration();
    export var paused = ko.observable<boolean>(false);
    export var metricsVisible = ko.observable<boolean>(true);
    export var metricsWidth = ko.observable<boolean>(true);
    export var mode: KnockoutObservable<string> = ko.observable("graph");
    export var incomingFragment: string;
    export var metricsList: MetricsBrowseList = new MetricsBrowseList();

    export var sliderChanged: (event: Event, ui: any) => void = (event, ui) => { setViewDuration(ui.values); };
    export var removeGraph: (s: GraphSpec) => void = (spec: GraphSpec) => {
            var id = getGraphName(spec);
            var graph = graphsById[id];
            if (graph != undefined) {
                graph.shutdown();
                graphs.remove(graph);
                $("#graph_div_" + graph.id).remove();
                delete graphsById[id];
            }
            //Remove the subscription so new connections wont receive the data
            subscriptions = subscriptions.filter((element: GraphSpec) => {
                return !(element.metric == spec.metric && element.service == spec.service && element.statistic == spec.statistic);
            });
            //Make sure to unsubscribe from the graph feed.
            Hosts.connections().forEach((element: ConnectionVM) => {
                element.model.protocol.unsubscribeFromMetric(spec);
            });
        };

    export var removeGraphVM: (v: StatisticView) => void = (view: StatisticView) => {
        removeGraph(view.spec);
    };

    export var fragment = ko.computed(() => {
        var servers = Hosts.connections().map((element) => { return element.server });
        var mygraphs = graphs().map((element: StatisticView) => {
            return { service: element.spec.service, metric: element.spec.metric, stat: element.spec.statistic };
        });
        var obj = { connections: servers, graphs: mygraphs, showMetrics: metricsVisible(), mode: mode() };
        return "#graph/" + encodeURIComponent(JSON.stringify(obj));
    });
    export var searchQuery = ko.observable<string>('').extend({throttle: 500});
    export var graphWidth = ko.observable<string>('col-md-4');
    searchQuery.subscribe((searchTerm) => { metricsList.search(searchTerm); });

    export var getGraphWidth = ko.computed(function() {
        return graphWidth();
    });

    var previousTimestamp: number = 0.0;
    export var render = (timestamp: number) => {
        // Render
        if (previousTimestamp != null) {
            var currentRate = 1000 / (timestamp - previousTimestamp);
            if (currentRate > targetFrameRate) {
                // Delayed animate
                var stepTimeInMillis = 1000 * (1 / targetFrameRate - 1 / currentRate);
                setTimeout(
                    () => {
                        window.requestAnimationFrame(render);
                    },
                    stepTimeInMillis);
                return;
            }
        }

        previousTimestamp = timestamp;

        graphs().forEach((graph) => {
            graph.render();
        });

        window.requestAnimationFrame(render)
    };
    window.requestAnimationFrame(render);

    export var doShade = ko.computed(function() {
        return Hosts.connections().some(function(element: ConnectionVM) {
            return element.selected();
        })
    });

    export var shouldShade = ko.computed(function() {
        return doShade()
    });

    fragment.subscribe((newValue) => { app.trigger("fragment:update", newValue); });

    export var activate = (fragment) => {
        incomingFragment = fragment;

    };

    export var attached = () => {
        if (incomingFragment !== undefined && incomingFragment !== null) {
            parseFragment(incomingFragment);
        }
        // TODO: Rewrite for KO bindings
        $('.sort-parent').sortable({
            items: '.sortable',
            connectWith: ".sort-parent"
        });

        app.on('opened').then(function(cvm) {
            subscribeToOpenedGraphs(cvm);
        });

        app.trigger("activate-graph-view");
    };

    export var toggleMetricsVisible = () => {
        metricsVisible(!metricsVisible());
    };

    export var setMetricsWidth: () => void = () => {
        metricsWidth(metricsVisible());
    };

    export var togglePause = () => {
        paused(!paused());
        for (var i = 0; i < graphs().length; i++) {
            graphs()[i].setPause(paused());
        }
    };

    export var subscribe = (cvm: ConnectionVM, spec: GraphSpec) => {
        cvm.model.protocol.subscribeToMetric(spec);
    };

    export var addGraph = (graphSpec: GraphSpec) => {
        var displayName = graphSpec.metric + " (" + graphSpec.statistic + ")";
        var id = getGraphName(graphSpec);
        subscriptions.push(graphSpec);
        Hosts.connections().forEach((cvm) => {
            subscribe(cvm, graphSpec);
        });
        var existing = graphsById[id];
        if (existing != undefined) {
            return;
        }

        var graph: StatisticView;
        if (mode() == "graph") {
            graph = new GraphVM(id, displayName, graphSpec);
        } else if (mode() == "gauge") {
            graph = new GaugeVM(id, displayName, graphSpec);
        }

        graph.setViewDuration(viewDuration);
        graph.targetFrameRate = targetFrameRate;
        graphsById[id] = graph;
        graphs.push(graph);
    };

    export var startGraph = (graphElement: HTMLElement, index: number, gvm: StatisticView) => {
        gvm.start(paused());
    };

    export var disconnect = (cvm: ConnectionVM) => {
        graphs().forEach((graph: StatisticView) => { graph.disconnectConnection(cvm); });
    };

    export var setViewDuration = (window: {min: number; max: number}) => {
        viewDuration = new ViewDuration(window.min, window.max);
        for (var i = 0; i < graphs().length; i++) {
            graphs()[i].setViewDuration(viewDuration);
            graphs()[i].render();
        }
    };

    export var parseFragment = (fragment: string) => {
        var toParse = fragment;
        if (toParse.substr(0, 7) == "#graph/") {
            toParse = toParse.substr(7);
        }

        var obj = JSON.parse(toParse);
        if (obj == null) {
            return;
        }

        var servers = obj.connections;
        var graphs = obj.graphs;
        mode(obj.mode || "graph");
        var showMetrics = obj.showMetrics;
        if (showMetrics === null || showMetrics === undefined) {
            showMetrics = true;
        }
        metricsVisible(showMetrics);

        servers.forEach((server) => {
            Hosts.connectToServer(server);
        });

        graphs.forEach((graph) => {
            addGraph(new GraphSpec(graph.service, graph.metric, graph.stat));
        });
    };

    export var idify = (value: string): string => {
        value = value.replace(/ /g, "_").toLowerCase();
        return value.replace(/\//g, "_");
    };

    export var getGraphName = (spec: GraphSpec) => {
        return idify(spec.service) + "_" + idify(spec.metric) + "_" + idify(spec.statistic);
    };

    export var loadFolderMetricsList = (newMetrics: MetricsListData): void => {
        metricsList.bind(newMetrics.metrics);
    };

    export var reportData = (report: ReportData, cvm: ConnectionVM) => {
        var graphName = getGraphName(new GraphSpec(report.service, report.metric, report.statistic));
        var graph = graphsById[graphName];
        if (graph != undefined) {
            graph.postData(report.server, report.timestamp, report.data, cvm);
        }
    };

    export var subscribeToOpenedGraphs =  (cvm: ConnectionVM) => {
        subscriptions.forEach((item: GraphSpec) => {
            subscribe(cvm, item);
        });
    };

    export var switchGraphLayout = () => {
        if (graphLayout == 'GRID') {
            graphLayout = 'ROW';
            $('.graph-container.col-md-4').each(function(index, element) { $(element).removeClass('col-md-4').addClass('col-md-12') });
            $('#graph-icon').prop("title", "Click for Grid Layout");
            $('#graph-icon').removeClass('fa-align-justify');
            $('#graph-icon').addClass('fa-th-large');
            graphWidth('');
        } else {
            graphLayout = 'GRID';
            $('.graph-container').each(function(index, element) { $(element).addClass('col-md-4').removeClass('col-md-12') });
            $('#graph-icon').prop("title", "Click for Row Layout");
            $('#graph-icon').removeClass('fa-th-large');
            $('#graph-icon').addClass('fa-align-justify');
            graphWidth('col-md-4');
        }
    };

    export var switchRenderRate = () => {
        if (targetFrameRate == 60) {
            targetFrameRate = 1;
            $('#render-icon').prop("title", "Click for Continuous");
            $('#render-icon').removeClass('fa-spinner');
            $('#render-icon').addClass('fa-circle-o-notch');
        } else {
            targetFrameRate = 60;
            $('#render-icon').prop("title", "Click for Stepped");
            $('#render-icon').removeClass('fa-circle-o-notch');
            $('#render-icon').addClass('fa-spinner');
        }
        ko.utils.arrayForEach(graphs(), (graph: StatisticView) => { graph.targetFrameRate = targetFrameRate });
    };
    console.log("done defining GVM");

    var skipSort = false;
    var graphLayout = 'GRID';
    var targetFrameRate = 1;
    var requireJsForceLoadKnockoutBindings = kob;
}

export = GraphViewModel;
