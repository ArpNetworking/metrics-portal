/*
 * Copyright 2020 Dropbox, Inc.
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

package global;

import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.ebean.config.ServerConfig;
import io.ebean.event.ServerConfigStartup;

/**
 * Plugin class to configure Ebean's {@link ServerConfig} at runtime.
 * <p>
 * This is necessary for configuring the Ebean server with dependencies
 * constructed via Guice, for instance. This class will be invoked for every
 * instance of {@code EbeanServer}.
 * <p>
 * NOTE: This <b>must</b> be used alongside an injector, since its whole purpose
 * is a shim around Ebean's lack of Guice support.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public class MetricsPortalServerConfigStartup implements ServerConfigStartup {
    @Inject
    private static @Nullable ObjectMapper gObjectMapper;
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsPortalServerConfigStartup.class);

    /**
     * Public default constructor. Required for injection.
     */
    public MetricsPortalServerConfigStartup() {}

    @Override
    public void onStart(final ServerConfig serverConfig) {
        LOGGER.info().setMessage("Initializing Ebean ServerConfig").log();
        if (gObjectMapper == null) {
            // In some cases we manually load the ebean model classes via
            // ServerConfig#addPackage (see EbeanServerHelper).
            //
            // If this class is accidentally instantiated in those environments,
            // then injection won't occur and we'll silently overwrite the
            // configured ObjectMapper with null. Explicitly throwing makes this
            // error appear obvious.
            throw new IllegalStateException("ObjectMapper is null - was this class loaded manually outside of Play?");
        }
        serverConfig.setObjectMapper(gObjectMapper);
    }
}
