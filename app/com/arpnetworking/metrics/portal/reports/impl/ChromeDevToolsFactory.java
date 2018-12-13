/*
 * Copyright 2018 Dropbox
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
package com.arpnetworking.metrics.portal.reports.impl;

import com.github.kklisura.cdt.launch.ChromeLauncher;
import com.github.kklisura.cdt.services.ChromeDevToolsService;
import com.github.kklisura.cdt.services.ChromeService;
import com.github.kklisura.cdt.services.types.ChromeTab;
import com.google.inject.Inject;

public class ChromeDevToolsFactory {

    private final ChromeLauncher launcher = new ChromeLauncher();
    private final ChromeService chromeService = launcher.launch(true);

    @Inject
    public ChromeDevToolsFactory() {}

    public ChromeDevToolsServiceWrapper create(boolean ignoreCertificateErrors) {
        final ChromeTab tab = chromeService.createTab();
        ChromeDevToolsService result = chromeService.createDevToolsService(tab);
        if (ignoreCertificateErrors) {
            result.getSecurity().setIgnoreCertificateErrors(true);
        }
        return new ChromeDevToolsServiceWrapper(result);
    }
}
