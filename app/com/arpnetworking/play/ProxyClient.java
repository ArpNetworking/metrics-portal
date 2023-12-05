/*
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

import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.http.HttpEntity;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import play.mvc.StatusHeader;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

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
     *
     * @return Streaming response
     */
    public CompletionStage<Result> proxy(
            final String path,
            final play.mvc.Http.Request request) {

        // TODO(brandon): Would be nice to have a streaming api for request body
        // Possibly implement a BodyParser that returns the Accumulator?
        // See: https://www.playframework.com/documentation/2.8.x/JavaBodyParsers#Directing-the-body-elsewhere
        final ByteString body = request.body().asBytes();
        final URI uri = _baseUri.resolve(path);
        final boolean isHttp10 = request.version().equals("HTTP/1.0");

        WSRequest wsrequest = _client.url(uri.toString());

        for (final Map.Entry<String, String[]> entry : request.queryString().entrySet()) {
            for (final String val : entry.getValue()) {
                wsrequest = wsrequest.addQueryParameter(entry.getKey(), val);
            }
        }

        wsrequest = wsrequest.setMethod(request.method());
        for (final Map.Entry<String, List<String>> entry : request.getHeaders().asMap().entrySet()) {
            for (final String val : entry.getValue()) {
                wsrequest = wsrequest.addHeader(entry.getKey(), val);
            }
        }
        if (body != null) {
            wsrequest = wsrequest.setBody(Source.single(ByteString.fromByteBuffer(body.asByteBuffer())));
        }

        return wsrequest.stream().thenApply(resp -> {
            final Map<String, List<String>> entries = resp.getHeaders();
            final Optional<Long> length = resp.getSingleHeader(Http.HeaderNames.CONTENT_LENGTH).map(Long::parseLong);

            final Optional<String> contentType = resp.getSingleHeader(Http.HeaderNames.CONTENT_TYPE)
                    .or(() -> length.map(v -> v == 0 ? "text/html" : null));

            final StatusHeader status = Results.status(resp.getStatus());
            Result result = status.sendEntity(
                    new HttpEntity.Streamed(
                            resp.getBodyAsSource(),
                            length,
                            contentType));

            final ArrayList<String> headersList = entries.entrySet()
                    .stream()
                    .filter(entry -> !FILTERED_HEADERS.contains(entry.getKey()))
                    .reduce(Lists.newArrayList(), (a, b) -> {
                        a.add(b.getKey());
                        a.add(b.getValue().get(0));
                        return a;
                    }, (a, b) -> {
                        a.addAll(b);
                        return b;
                    });
            final String[] headerArray = headersList.toArray(new String[0]);
            result = result.withHeaders(headerArray);

            if (isHttp10) {
                // Strip the transfer encoding header as chunked isn't supported in 1.0
                result = result.withoutHeader(Http.HeaderNames.TRANSFER_ENCODING);
                // Strip the connection header since we don't support keep-alives in 1.0
                result = result.withoutHeader(Http.HeaderNames.CONNECTION);
            }

            return result;
        });
    }

    private final URI _baseUri;
    private final WSClient _client;
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyClient.class);
    private static final Set<String> FILTERED_HEADERS = Sets.newHashSet(
            Http.HeaderNames.CONTENT_TYPE,
            Http.HeaderNames.CONTENT_LENGTH,
            Http.HeaderNames.TRANSFER_ENCODING);

}

