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

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.commons.java.util.function.SingletonSupplier;
import com.arpnetworking.testing.SerializationTestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import play.mvc.Result;
import play.test.Helpers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
        urlBuilder.append(getEnvOrDefault("METRICS_PORTAL_PORT", "8080"));
        if (!path.startsWith("/")) {
            urlBuilder.append("/");
        }
        urlBuilder.append(path);
        return urlBuilder.toString();
    }

    /**
     * Return the response content as a {@code JsonNode}.
     *
     * @param response the {@code CloseableHttpResponse} to read
     * @return response content as a {@code JsonNode}
     * @throws IOException if reading the response content fails
     */
    public static JsonNode readContentAsJson(final CloseableHttpResponse response) throws IOException {
        return ObjectMapperFactory.getInstance().readTree(response.getEntity().getContent());
    }

    /**
     * Return the response content as a {@code String}.
     *
     * @param response the {@code CloseableHttpResponse} to read
     * @return response content as a {@code String}
     * @throws IOException if reading the response content fails
     */
    public static String readContentAsString(final CloseableHttpResponse response) throws IOException {
        return readContent(response).toString("UTF-8");
    }

    /**
     * Return the response content as an arbitrary class, by deserializing with Jackson.
     *
     * @param <T> the type of object to deserialize
     * @param response the {@code CloseableHttpResponse} to read
     * @param clazz the class to deserialize
     * @return response content as a {@code T}
     * @throws IOException if reading the response content fails
     */
    public static <T> T readContentAs(final CloseableHttpResponse response, final Class<T> clazz) throws IOException {
        return SerializationTestUtils.getApiObjectMapper().readValue(readContentAsString(response), clazz);
    }

    /**
     * Return the response content as a {@code JsonNode}.
     *
     * @param result the play {@code Result} to read
     * @return response content as a {@code JsonNode}
     * @throws IOException if reading the response content fails
     */
    public static JsonNode readContentAsJson(final Result result) throws IOException {
        return SerializationTestUtils.getApiObjectMapper().readTree(Helpers.contentAsString(result));
    }

    /**
     * Return the response content as an arbitrary class, by deserializing with Jackson.
     *
     * @param <T> the type of object to deserialize
     * @param result the play {@code Result} to read
     * @param clazz the class to deserialize
     * @return response content as a {@code T}
     * @throws IOException if reading the response content fails
     */
    public static <T> T readContentAs(final Result result, final Class<T> clazz) throws IOException {
        return SerializationTestUtils.getApiObjectMapper().readValue(Helpers.contentAsString(result), clazz);
    }

    private static ByteArrayOutputStream readContent(final CloseableHttpResponse response) throws IOException {
        final ByteArrayOutputStream result = new ByteArrayOutputStream();
        final byte[] buffer = new byte[1024];
        int length;
        while ((length = response.getEntity().getContent().read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result;
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
