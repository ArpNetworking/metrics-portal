/**
 * Copyright 2015 Groupon.com
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
package com.arpnetworking.play;

import akka.util.ByteString;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.libs.ws.WSClient;
import play.shaded.ahc.org.asynchttpclient.AsyncHandler;
import play.shaded.ahc.org.asynchttpclient.RequestBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * A simple proxy client.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public final class ProxyClient {
    /**
     * Public constructor.
     *
     * @param baseUri the base url for the proxy
     * @param client ws client to use
     */
    @AssistedInject
    public ProxyClient(@Assisted final URI baseUri, final WSClient client) {
        _baseUri = baseUri;
        _client = client;
    }

    /**
     * Proxy a request.
     *
     * @param path the path to proxy
     * @param request the request
     * @param handler a handler to execute as a callback
     * @param <T> the type of the handler
     */
    public <T> void proxy(
            final String path,
            final play.mvc.Http.Request request,
            final AsyncHandler<T> handler) {

        final ByteString body = request.body().asBytes();
        final URI uri = _baseUri.resolve(path);

        final RequestBuilder builder = new RequestBuilder();
        for (final Map.Entry<String, String[]> entry : request.queryString().entrySet()) {
            for (final String val : entry.getValue()) {
                builder.addQueryParam(entry.getKey(), val);
            }
        }

        builder.setUrl(uri.toString());
        builder.setMethod(request.method());
        for (final Map.Entry<String, List<String>> entry : request.getHeaders().toMap().entrySet()) {
            for (final String val : entry.getValue()) {
                builder.setHeader(entry.getKey(), val);
            }
        }
        if (body != null) {
            builder.setBody(body.asByteBuffer());
        }

        final Object underlying = _client.getUnderlying();
        if (underlying instanceof play.shaded.ahc.org.asynchttpclient.AsyncHttpClient) {
            final play.shaded.ahc.org.asynchttpclient.AsyncHttpClient client =
                    (play.shaded.ahc.org.asynchttpclient.AsyncHttpClient) underlying;
            client.executeRequest(builder.build(), handler);
        } else {
            throw new RuntimeException("Unknown AsyncHttpClient '" + underlying.getClass().getCanonicalName() + "'");
        }
    }

    private final URI _baseUri;
    private final WSClient _client;
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyClient.class);
}

