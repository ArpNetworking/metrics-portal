/*
 * Copyright 2014 Brandon Arp
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

/// <reference path="libs/durandal/durandal.d.ts"/>
/// <amd-dependency path="debug"/>
/// <amd-dependency path="text"/>
/// <amd-dependency path="jquery"/>
/// <amd-dependency path="jquery.ui"/>
/// <amd-dependency path="bean"/>
/// <amd-dependency path="underscore"/>
/// <amd-dependency path="jqrangeslider"/>
/// <amd-dependency path="bootstrap"/>
/// <amd-dependency path="d3"/>
/// <amd-dependency path="gauge"/>
/// <amd-dependency path="knockout"/>
/// <amd-dependency path="knockout-fast-foreach"/>
/// <amd-dependency path="classes/KnockoutBindings"/>
/// <amd-dependency path="classes/GraphViewModel"/>

import system = require('durandal/system');
import app = require('durandal/app');
import viewLocator = require('durandal/viewLocator');
import ko = require('knockout');

app.title = "M-Portal";
app.configurePlugins({
    router: true,
    dialog: true,
    widget: true
});

viewLocator.useConvention('classes', '/assets/html');

app.start().then(function() {
    app.setRoot("../classes/shell");
});
