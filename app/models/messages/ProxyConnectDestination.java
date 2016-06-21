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

import java.net.URI;

/**
 * Message sent when web socket connection is established to the proxy destination.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public class ProxyConnectDestination {

    /**
     * Public constructor.
     *
     * @param uri The destination's uri.
     */
    public ProxyConnectDestination(final URI uri) {
        _uri = uri;
    }

    public final URI getUri() {
        return _uri;
    }

    private final URI _uri;
}
