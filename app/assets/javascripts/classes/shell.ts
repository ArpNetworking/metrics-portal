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

import * as router from 'durandal/plugins/router';

class shell {
    router = router;
    activate() {
        router.map([
            { route: '', title: 'Telemetry', moduleId: 'classes/GraphViewModel', nav: false },
            { route: 'graph/*spec', title: 'Telemetry', moduleId: 'classes/GraphViewModel', nav: false },
            { route: 'host-registry', title: 'Host Registry', moduleId: 'classes/hostregistry/HostRegistryViewModel', nav: false },
            { route: 'expressions', title: 'Expressions', moduleId: 'classes/expressions/ExpressionsViewModel', nav: false },
            { route: 'alerts', title: 'Alerts', moduleId: 'classes/alerts/AlertsViewModel', nav: false },
            { route: 'alert/edit(/:id)', title: 'Alerts', moduleId: 'classes/alerts/EditAlertViewModel', nav: false },
            { route: 'reports', title: 'Reports', moduleId: 'classes/reports/ReportsViewModel', nav: false },
            { route: 'report/edit(/:id)', title: 'Reports', moduleId: 'classes/reports/EditReportViewModel', nav: false }
        ]).buildNavigationModel();

        return router.activate();
    }
}

export default shell;
