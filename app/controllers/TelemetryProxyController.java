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
package controllers;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.model.ws.WebSocketRequest;
import akka.http.javadsl.model.ws.WebSocketUpgradeResponse;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import com.google.inject.Inject;
import models.internal.Features;
import play.mvc.Controller;
import play.mvc.WebSocket;

import java.net.URISyntaxException;
import java.util.concurrent.CompletionStage;

/**
 * Telemetry streaming proxy controller.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public class TelemetryProxyController extends Controller {

    /**
     * Public constructor.
     *
     * @param system The {@code ActorSystem} instance.
     * @param features The {@code Features} instance.
     */
    @Inject
    public TelemetryProxyController(
            final ActorSystem system,
            final Features features) {
        _system = system;
        _enabled = features.isProxyEnabled();
        _materializer = Materializer.createMaterializer(_system);
    }

    /**
     * Proxy the specified stream.
     *
     * @param uri The uri of the stream to proxy.
     * @return Proxied web socket stream.
     * @throws URISyntaxException if supplied uri is invalid.
     */
    public WebSocket stream(final String uri) throws URISyntaxException {
        if (!_enabled) {
            throw new IllegalStateException("Proxy feature is disabled");
        }

        final Http http = Http.get(_system);

        final Flow<String, Message, NotUsed> mapFlow = Flow.<String>create()
                .map(TextMessage::create);
        final Flow<Message, String, CompletionStage<WebSocketUpgradeResponse>> wsFlow =
                http.webSocketClientFlow(WebSocketRequest.create(uri))
                .mapAsync(1, m -> m.asTextMessage().getStreamedText().runFold("", (a, b) -> a + b, _materializer));

        // Accept web socket connection from proxy originator
        return WebSocket.Text.accept(header -> mapFlow.via(wsFlow));
    }


    private Materializer _materializer;
    private final ActorSystem _system;
    private final boolean _enabled;
}
