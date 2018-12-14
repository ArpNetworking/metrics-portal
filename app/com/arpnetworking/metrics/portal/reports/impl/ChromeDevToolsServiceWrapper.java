/*
 * Copyright 2018 Dropbox, Inc.
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

import com.github.kklisura.cdt.services.ChromeDevToolsService;

import java.util.Base64;
import java.util.function.Consumer;

public class ChromeDevToolsServiceWrapper implements com.arpnetworking.metrics.portal.reports.impl.ChromeDevToolsService {
    private final ChromeDevToolsService dts;
    public ChromeDevToolsServiceWrapper(ChromeDevToolsService dts) {
        this.dts = dts;
    }
    public Object evaluate(String js) {
        return dts.getRuntime().evaluate(js).getResult().getValue();
    }
    public byte[] printToPdf(double pageWidthInches, double pageHeightInches) {
        return Base64.getDecoder().decode(dts.getPage().printToPDF(
                false,
                false,
                false,
                1.0,
                pageWidthInches,
                pageHeightInches,
                0.4,
                0.4,
                0.4,
                0.4,
                "",
                true,
                "",
                "",
                true
        ));
    }
    public void navigate(String url) {
        dts.getPage().navigate(url);
    }
    public void onLoad(Runnable callback) {
        dts.getPage().enable(); dts.getPage().onLoadEventFired(e -> callback.run());
    }
    public void onLog(Consumer<String> callback) {
        dts.getConsole().enable();
        dts.getConsole().onMessageAdded(e -> callback.accept(e.getMessage().getText()));
    }
    public void close() {
        dts.close();
    }
}
