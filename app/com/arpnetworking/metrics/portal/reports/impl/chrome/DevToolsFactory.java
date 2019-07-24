/*
 * Copyright 2019 Dropbox, Inc.
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
package com.arpnetworking.metrics.portal.reports.impl.chrome;

import java.util.Map;

/**
 * A factory that sits atop a Chrome instance and creates tabs / dev-tools instances.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public interface DevToolsFactory {
    /**
     * Create a {@link DevToolsService}.
     *
     * @param ignoreCertificateErrors whether the created tab should ignore certificate errors when loading resources.
     * @param chromeArgs any extra command-line flags that should be passed to Chrome.
     * @return the created service.
     */
    DevToolsService create(boolean ignoreCertificateErrors, Map<String, Object> chromeArgs);
}
