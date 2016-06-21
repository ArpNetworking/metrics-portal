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
package models.messages;

import play.mvc.WebSocket;

/**
 * Message sent when web socket connection is established to the proxy originator.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public class ProxyConnectOriginator {

    /**
     * Public constructor.
     *
     * @param in The originator's inbound web socket.
     * @param out The originator's outbound web socket.
     */
    public ProxyConnectOriginator(
            final WebSocket.In<String> in,
            final WebSocket.Out<String> out) {
        _in = in;
        _out = out;
    }

    public final WebSocket.In<String> getIn() {
        return _in;
    }

    public final WebSocket.Out<String> getOut() {
        return _out;
    }

    private final WebSocket.In<String> _in;
    private final WebSocket.Out<String> _out;
}
