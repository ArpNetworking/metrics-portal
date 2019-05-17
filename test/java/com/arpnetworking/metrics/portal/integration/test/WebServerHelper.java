/*
 * Copyright 2019 Dropbox
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
package com.arpnetworking.metrics.portal.integration.test;

import com.arpnetworking.commons.java.util.function.SingletonSupplier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Helper to communicate with the web server during integration testing.
 *
 * @author ville dot koskela at inscopemetrics dot io
 */
public final class WebServerHelper {

    /**
     * Return shared HTTP client instance.
     *
     * @return the shared HTTP client instance
     */
    public static CloseableHttpClient getClient() {
        return HTTP_CLIENT_SUPPLIER.get();
    }


    /**
     * Return the uri for a given path to the web server instance under test.
     *
     * @param path the relative path
     * @return the full uri
     */
    public static String getUri(final String path) {
        final StringBuilder urlBuilder = new StringBuilder("http://");
        urlBuilder.append(getEnvOrDefault("METRICS_PORTAL_HOST", "localhost"));
        urlBuilder.append(":");
        urlBuilder.append(getEnvOrDefault("METRICS_PORTA_PORT", "8080"));
        if (!path.startsWith("/")) {
            urlBuilder.append("/");
        }
        urlBuilder.append(path);
        return urlBuilder.toString();
    }

    private static String getEnvOrDefault(final String name, final String defaultValue) {
        @Nullable final String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }

    private WebServerHelper() {}

    private static final int PARALLELISM = 100;
    private static final Supplier<CloseableHttpClient> HTTP_CLIENT_SUPPLIER = new SingletonSupplier<>(() -> {
        final SingletonSupplier<PoolingHttpClientConnectionManager> clientManagerSupplier = new SingletonSupplier<>(() -> {
            final PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
            connectionManager.setDefaultMaxPerRoute(PARALLELISM);
            connectionManager.setMaxTotal(PARALLELISM);
            return connectionManager;
        });

        return HttpClients.custom()
                .setConnectionManager(clientManagerSupplier.get())
                .disableCookieManagement()
                .build();
    });
}
