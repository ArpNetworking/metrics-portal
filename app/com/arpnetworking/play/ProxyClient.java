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

import akka.stream.javadsl.JavaFlowSupport;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamConverters;
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
import play.libs.ws.WSResponse;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import play.mvc.StatusHeader;
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders;
import play.shaded.ahc.org.asynchttpclient.AsyncCompletionHandler;
import play.shaded.ahc.org.asynchttpclient.AsyncHandler;
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart;
import play.shaded.ahc.org.asynchttpclient.HttpResponseHeaders;
import play.shaded.ahc.org.asynchttpclient.HttpResponseStatus;
import play.shaded.ahc.org.asynchttpclient.RequestBuilder;
import play.shaded.ahc.org.asynchttpclient.Response;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
     * @param handler a handler to execute as a callback
     * @param <T> the type of the handler
     */
    public <T> CompletionStage<Result> proxy(
            final String path,
            final play.mvc.Http.Request request) {

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
        for (final Map.Entry<String, List<String>> entry : request.getHeaders().toMap().entrySet()) {
            for (final String val : entry.getValue()) {
                wsrequest = wsrequest.addHeader(entry.getKey(), val);
            }
        }
        if (body != null) {
            wsrequest = wsrequest.setBody(Source.single(ByteString.fromByteBuffer(body.asByteBuffer())))
        }

        wsrequest.stream().thenApply(resp -> {
            final Map<String, List<String>> entries = resp.getHeaders();
            final Optional<Long> length = resp.getSingleHeader(Http.HeaderNames.CONTENT_LENGTH).map(Long::parseLong);

            final Optional<String> contentType = resp.getSingleHeader(Http.HeaderNames.CONTENT_TYPE)
                    .();
            if (entries.get(CONTENT_TYPE) != null) {
                contentType = entries.get(CONTENT_TYPE);
            } else if (length.isPresent() && length.get() == 0) {
                contentType = "text/html";
            } else {
                contentType = null;
            }

            final StatusHeader status = Results.status(_status);
            Result result = status.sendEntity(
                    new HttpEntity.Streamed(
                            StreamConverters.fromInputStream(() -> _inputStream, DEFAULT_CHUNK_SIZE),
                            length,
                            Optional.ofNullable(contentType)));

            final ArrayList<String> headersList = entries.entries()
                    .stream()
                    .filter(entry -> !FILTERED_HEADERS.contains(entry.getKey()))
                    .reduce(Lists.newArrayList(), (a, b) -> {
                        a.add(b.getKey());
                        a.add(b.getValue());
                        return a;
                    }, (a, b) -> {
                        a.addAll(b);
                        return b;
                    });
            final String[] headerArray = headersList.toArray(new String[0]);
            result = result.withHeaders(headerArray);

            if (_isHttp10) {
                // Strip the transfer encoding header as chunked isn't supported in 1.0
                result = result.withoutHeader(TRANSFER_ENCODING);
                // Strip the connection header since we don't support keep-alives in 1.0
                result = result.withoutHeader(CONNECTION);
            }

            _promise.complete(result);

        });


        handler = new ResponseHandler(promise, isHttp10);
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

    private static class ResponseHandler extends AsyncCompletionHandler<Void> {
        ResponseHandler(
                final CompletableFuture<Result> promise,
                final boolean isHttp10) {
            try {
                _outputStream = new PipedOutputStream();
                _inputStream = new PipedInputStream(_outputStream);
                _promise = promise;
                _isHttp10 = isHttp10;
            } catch (final IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public State onStatusReceived(final HttpResponseStatus status) {
            _status = status.getStatusCode();
            return State.CONTINUE;
        }

        @Override
        public State onBodyPartReceived(final HttpResponseBodyPart content) throws Exception {
            _outputStream.write(content.getBodyPartBytes());

            if (content.isLast()) {
                _outputStream.flush();
                _outputStream.close();
            }
            return State.CONTINUE;
        }

        @Override
        public State onHeadersReceived(final HttpResponseHeaders headers) {
            try {
                final HttpHeaders entries = headers.getHeaders();
                Optional<Long> length = Optional.empty();
                if (entries.contains(CONTENT_LENGTH)) {
                    final String clen = entries.get(CONTENT_LENGTH);
                    length = Optional.of(Long.parseLong(clen));
                }
                final String contentType;
                if (entries.get(CONTENT_TYPE) != null) {
                    contentType = entries.get(CONTENT_TYPE);
                } else if (length.isPresent() && length.get() == 0) {
                    contentType = "text/html";
                } else {
                    contentType = null;
                }

                final StatusHeader status = Results.status(_status);
                Result result = status.sendEntity(
                        new HttpEntity.Streamed(
                                StreamConverters.fromInputStream(() -> _inputStream, DEFAULT_CHUNK_SIZE),
                                length,
                                Optional.ofNullable(contentType)));

                final ArrayList<String> headersList = entries.entries()
                        .stream()
                        .filter(entry -> !FILTERED_HEADERS.contains(entry.getKey()))
                        .reduce(Lists.newArrayList(), (a, b) -> {
                            a.add(b.getKey());
                            a.add(b.getValue());
                            return a;
                        }, (a, b) -> {
                            a.addAll(b);
                            return b;
                        });
                final String[] headerArray = headersList.toArray(new String[0]);
                result = result.withHeaders(headerArray);

                if (_isHttp10) {
                    // Strip the transfer encoding header as chunked isn't supported in 1.0
                    result = result.withoutHeader(TRANSFER_ENCODING);
                    // Strip the connection header since we don't support keep-alives in 1.0
                    result = result.withoutHeader(CONNECTION);
                }

                _promise.complete(result);
                return State.CONTINUE;
                // CHECKSTYLE.OFF: IllegalCatch - We need to return a response no matter what
            } catch (final Throwable e) {
                // CHECKSTYLE.ON: IllegalCatch
                _promise.completeExceptionally(e);
                throw e;
            }
        }

        @Override
        public void onThrowable(final Throwable t) {
            try {
                _outputStream.close();
                _promise.completeExceptionally(t);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
            super.onThrowable(t);
        }

        @Override
        public Void onCompleted(final Response response) {
            try {
                _outputStream.flush();
                _outputStream.close();
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        }

        private int _status;
        private final PipedOutputStream _outputStream;
        private final PipedInputStream _inputStream;
        private final CompletableFuture<Result> _promise;
        private final boolean _isHttp10;
        private static final int DEFAULT_CHUNK_SIZE = 8 * 1024;
        private static final Set<String> FILTERED_HEADERS = Sets.newHashSet(CONTENT_TYPE, CONTENT_LENGTH, TRANSFER_ENCODING);
    }
}

