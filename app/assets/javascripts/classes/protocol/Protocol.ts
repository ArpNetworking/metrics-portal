/*
 * Copyright 2014 Groupon.com
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

import ConnectionVM from "../ConnectionVM";
import GraphSpec from "../GraphSpec";

// This interface exposes the protocol between Metrics Portal and ReMet Proxy.
// All interaction with the ReMet Proxy must go through this protocol interface.
// Concrete implementations will need to translate the methods here into messages
// to send to ReMet Proxy.
interface Protocol {
    // Method called when ReMet Proxy sends a message to the GUI.
    processMessage(data: any, cvm: ConnectionVM): void;

    // Subscribes the GUI to metric updates from the proxy.
    // param spec: the metric to subscribe to
    subscribeToMetric(spec: GraphSpec): void;

    // Unsubscribes the GUI from metric updates from ReMet Proxy.
    // param spec: the metric to unsubscribe from
    unsubscribeFromMetric(spec: GraphSpec): void;

    // Method called when a connection is established to ReMet Proxy.
    connectionInitialized(): void;

    // Method called periodically while the connection is open.
    heartbeat(): void;
}

export default Protocol;
