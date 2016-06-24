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

import actors.ProxyConnection;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.arpnetworking.metrics.MetricsFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import models.messages.ProxyConnectDestination;
import models.messages.ProxyConnectOriginator;
import play.mvc.Controller;
import play.mvc.WebSocket;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

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
     */
    @Inject
    public ProxyController(final MetricsFactory metricsFactory, final ActorSystem system) {
        _metricsFactory = metricsFactory;
        _system = system;
    }

    /**
     * Proxy the specified stream.
     *
     * @param uri The uri of the stream to proxy.
     * @return Proxied stream.
     * @throws URISyntaxException if supplied uri is invalid.
     */
    public WebSocket<String> stream(final String uri) throws URISyntaxException {
        final ActorRef proxyActor = _system.actorOf(ProxyConnection.props(_metricsFactory));

        // Initiate web socket connection to proxy destination
        proxyActor.tell(new ProxyConnectDestination(new URI(uri)), ActorRef.noSender());

        // Accept web socket connection from proxy originator
        return new ProxyWebSocket(proxyActor);
    }

    private final MetricsFactory _metricsFactory;
    private final ActorSystem _system;
    private final Map<WebSocket.Out<JsonNode>, ActorRef> _connections = Maps.newHashMap();

    private static class ProxyWebSocket extends WebSocket<String> {

        /**
         * Public constructor.
         *
         * @param proxyActor Actor reference to proxy connection actor.
         */
        public ProxyWebSocket(final ActorRef proxyActor) {
            _proxyActor = proxyActor;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onReady(final In<String> in, final Out<String> out) {
            _proxyActor.tell(new ProxyConnectOriginator(in, out), ActorRef.noSender());
        }

        private final ActorRef _proxyActor;
    }
}
