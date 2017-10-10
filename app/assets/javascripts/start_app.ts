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
/// <reference types="requirejs" />
requirejs.config({
    deps: ["metrics_portal"],
    paths : {
        'bean' : '../lib/bean/bean.min', //Required by flotr2
        'bootstrap' : '../lib/bootstrap/js/bootstrap.min',
        'd3' : '../lib/d3/build/d3.min',
        'gauge' : 'gauge.min',

        //Durandal
        'durandal/activator': '../lib/durandal/js/activator',
        'durandal/app': '../lib/durandal/js/app',
        'durandal/binder': '../lib/durandal/js/binder',
        'durandal/composition': '../lib/durandal/js/composition',
        'durandal/events': '../lib/durandal/js/events',
        'durandal/system': '../lib/durandal/js/system',
        'durandal/transitions/entrance': '../lib/durandal/js/transitions/entrance',
        'durandal/viewEngine': '../lib/durandal/js/viewEngine',
        'durandal/viewLocator': '../lib/durandal/js/viewLocator',
        'plugins/dialog': '../lib/durandal/js/plugins/dialog',
        'plugins/history': '../lib/durandal/js/plugins/history',
        'plugins/http': '../lib/durandal/js/plugins/http',
        'plugins/observable': '../lib/durandal/js/plugins/observable',
        'plugins/router': '../lib/durandal/js/plugins/router',
        'plugins/serializer': '../lib/durandal/js/plugins/serializer',
        'plugins/widget': '../lib/durandal/js/plugins/widget',

        'flotr2' : '../lib/flotr2/flotr2.amd',
        'jquery' : '../lib/jquery/jquery.min',
        'jquery.ui' : '../lib/jquery-ui/jquery-ui.min',
        'jqrangeslider' : '../lib/jQRangeSlider/jQAllRangeSliders-withRuler-min',
        'knockout' : '../lib/knockout/knockout',
        'knockout-fast-foreach' : 'knockout-fast-foreach.min',
        'text' : '../lib/requirejs-text/text', //Required by durandal
        'typeahead' : '../lib/typeaheadjs/typeahead.bundle',
        'underscore' : '../lib/underscorejs/underscore-min' //Required by flotr2
    },
    shim : {
        'knockout-fast-foreach' : {
            deps : [ 'knockout']
        },
        'jquery.ui' : {
            deps : [ 'jquery' ]
        },
        'jqrangeslider' : {
            deps : [ 'jquery.ui' ]
        },
        'bootstrap' : {
            deps : [ 'jquery' ]
        },
        'typeahead' : {
            deps : [ 'jquery']
        }
    }
});
