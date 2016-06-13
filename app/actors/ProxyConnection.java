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
package actors;

import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import models.messages.ProxyConnectDestination;
import models.messages.ProxyConnectOriginator;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import play.mvc.WebSocket;

import java.net.URI;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Actor to proxy between two Web Socket connections.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public class ProxyConnection extends UntypedActor {

    /**
     * Public constructor.
     *
     * @param metricsFactory The <code>MetricsFactory</code> instance.
     */
    public ProxyConnection(final MetricsFactory metricsFactory) {
        _metricsFactory = metricsFactory;
    }

    /**
     * Factory for creating a <code>Props</code> with strong typing.
     *
     * @param metricsFactory Instance of <code>MetricsFactory</code>.
     * @return a new Props object to create a <code>ProxyConnection</code>.
     */
    public static Props props(final MetricsFactory metricsFactory) {
        return Props.create(
                ProxyConnection.class,
                metricsFactory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postStop() throws Exception {
        if (_originatorOut != null) {
            _originatorOut.close();
        }
        if (_destinationClient != null) {
            _destinationClient.close();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onReceive(final Object message) throws Exception {
        LOGGER.trace()
                .setMessage("Received message")
                .addData("actor", self())
                .addData("data", message)
                .log();

        if (message instanceof ProxyConnectOriginator) {
            // Originator connection
            final ProxyConnectOriginator proxyConnectOriginator = (ProxyConnectOriginator) message;
            _originatorIn = proxyConnectOriginator.getIn();
            _originatorOut = proxyConnectOriginator.getOut();

            // Add handler for close
            _originatorIn.onClose(() -> {
                LOGGER.info()
                        .setMessage("Originator connection closed")
                        .addData("actor", self())
                        .log();
                getSelf().tell(PoisonPill.getInstance(), getSelf());
            });

            // Add handler for data
            _originatorIn.onMessage(m -> self().tell(new OriginatorMessage(m), self()));

            // If we have both connections tie them together and flush buffers
            if (_destinationClient != null && org.java_websocket.WebSocket.READYSTATE.OPEN.equals(_destinationClient.getReadyState())) {
                establishProxy();
            }
        } else if (message instanceof ProxyConnectDestination) {
            // Destination connection
            final ProxyConnectDestination proxyConnectDestination = (ProxyConnectDestination) message;
            _destinationClient = new ProxyWebSocketClient(proxyConnectDestination.getUri());
            _destinationClient.connect();
        } else if (message instanceof DestinationConnected) {
            // If we have both connections tie them together and flush buffers
            if (_originatorIn != null && _originatorOut != null) {
                establishProxy();
            }
        } else if (message instanceof OriginatorMessage) {
            final OriginatorMessage originatorMessage = (OriginatorMessage) message;
            if (_isProxied) {
                _destinationClient.send(originatorMessage.getMessage());
            } else {
                _originatorMessageQueue.add(originatorMessage.getMessage());
            }
        } else if (message instanceof DestinationMessage) {
            final DestinationMessage destinationMessage = (DestinationMessage) message;
            if (_isProxied) {
                _originatorOut.write(destinationMessage.getMessage());
            } else {
                _destinationMessageQueue.add(destinationMessage.getMessage());
            }
        }
    }

    private void establishProxy() {
        LOGGER.info()
                .setMessage("Established proxy connection")
                .addData("actor", self())
                .addData("destination", _destinationClient.getURI())
                .log();

        // Proxy connection established
        if (_isProxied) {
            throw new IllegalStateException("Connection is already proxied");
        }
        _isProxied = true;

        // Send any buffered messages from the originator to the destination
        while (!_originatorMessageQueue.isEmpty()) {
            _destinationClient.send(_originatorMessageQueue.remove());
        }

        // Send any buffered messages from the destination to the originator
        while (!_destinationMessageQueue.isEmpty()) {
            ProxyConnection.this._originatorOut.write(_destinationMessageQueue.remove());
        }
    }

    private final MetricsFactory _metricsFactory;
    private final Queue<String> _originatorMessageQueue = new LinkedList<>();
    private final Queue<String> _destinationMessageQueue = new LinkedList<>();
    private boolean _isProxied = false;
    private WebSocket.In<String> _originatorIn;
    private WebSocket.Out<String> _originatorOut;
    private ProxyWebSocketClient _destinationClient;

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyConnection.class);

    private static final class DestinationConnected {}

    private static final class OriginatorMessage {
        OriginatorMessage(final String message) {
            _message = message;
        }

        public String getMessage() {
            return _message;
        }

        private final String _message;
    }

    private static final class DestinationMessage {
        DestinationMessage(final String message) {
            _message = message;
        }

        public String getMessage() {
            return _message;
        }

        private final String _message;
    }

    private final class ProxyWebSocketClient extends WebSocketClient {

        ProxyWebSocketClient(final URI uri) {
            super(uri);
        }

        @Override
        public void onOpen(final ServerHandshake handshakedata) {
            ProxyConnection.this.getSelf().tell(new DestinationConnected(), ProxyConnection.this.getSelf());
        }

        @Override
        public void onMessage(final String message) {
            ProxyConnection.this.getSelf().tell(new DestinationMessage(message), ProxyConnection.this.getSelf());
        }

        @Override
        public void onClose(final int code, final String reason, final boolean remote) {
            LOGGER.info()
                    .setMessage("Destination connection closed")
                    .addData("actor", self())
                    .addData("destination", getURI())
                    .log();
            ProxyConnection.this.getSelf().tell(PoisonPill.getInstance(), ProxyConnection.this.getSelf());
        }

        @Override
        public void onError(final Exception ex) {
            LOGGER.warn()
                    .setMessage("Destination connection error")
                    .addData("actor", self())
                    .addData("destination", getURI())
                    .setThrowable(ex)
                    .log();
            ProxyConnection.this.getSelf().tell(PoisonPill.getInstance(), ProxyConnection.this.getSelf());
        }
    }
}
