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

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChromeDevToolsFactory {

    private static @Nullable ChromeService chromeService = null;
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    private static ChromeService getService() {
        if (!initialized.getAndSet(true)) {
            chromeService = new ChromeLauncher().launch(true);
        }
        return chromeService;
    }

    public static ChromeDevToolsServiceWrapper create(boolean ignoreCertificateErrors) {
        final ChromeTab tab = getService().createTab();
        ChromeDevToolsService result = getService().createDevToolsService(tab);
        if (ignoreCertificateErrors) {
            result.getSecurity().setIgnoreCertificateErrors(true);
        }
        return new ChromeDevToolsServiceWrapper(result);
    }
}
