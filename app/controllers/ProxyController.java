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
package controllers;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.model.ws.WebSocketRequest;
import akka.http.javadsl.model.ws.WebSocketUpgradeResponse;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import com.arpnetworking.metrics.MetricsFactory;
import com.google.inject.Inject;
import models.internal.Features;
import play.mvc.Controller;
import play.mvc.WebSocket;

import java.net.URISyntaxException;
import java.util.concurrent.CompletionStage;

/**
 * Metrics portal proxy controller. Exposes API to proxy streaming to application hosts.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public class ProxyController extends Controller {

    /**
     * Public constructor.
     *
     * @param metricsFactory The <code>MetricsFactory</code> instance.
     * @param system The <code>ActorSystem</code> instance.
     * @param features The <code>Features</code> instance.
     */
    @Inject
    public ProxyController(
            final MetricsFactory metricsFactory,
            final ActorSystem system,
            final Features features) {
        _metricsFactory = metricsFactory;
        _system = system;
        _enabled = features.isProxyEnabled();
        _materializer = ActorMaterializer.create(_system);
    }

    /**
     * Proxy the specified stream.
     *
     * @param uri The uri of the stream to proxy.
     * @return Proxied stream.
     * @throws URISyntaxException if supplied uri is invalid.
     */
    public WebSocket stream(final String uri) throws URISyntaxException {
        if (!_enabled) {
            throw new IllegalStateException("Proxy disabled");
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


    private ActorMaterializer _materializer;
    private final MetricsFactory _metricsFactory;
    private final ActorSystem _system;
    private final boolean _enabled;
}
