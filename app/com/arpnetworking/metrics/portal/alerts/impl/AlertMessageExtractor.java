/**
 * Copyright 2017 Smartsheet
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
package com.arpnetworking.metrics.portal.alerts.impl;

import akka.cluster.sharding.ShardRegion;

/**
 * Extracts data from messages to setup alert executors.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class AlertMessageExtractor extends ShardRegion.HashCodeMessageExtractor {
    /**
     * Public constructor.
     */
    public AlertMessageExtractor() {
        super(NUM_SHARDS);
    }

    @Override
    public String entityId(final Object message) {
        if (message instanceof AlertExecutor.InstantiateAlert) {
            final AlertExecutor.InstantiateAlert msg = (AlertExecutor.InstantiateAlert) message;
            return msg.getAlertId().toString();
        } else if (message instanceof AlertExecutor.SendTo) {
            final AlertExecutor.SendTo msg = (AlertExecutor.SendTo) message;
            return msg.getAlertId().toString();
        }
        return null;
    }

//    @Override
//    public Object entityMessage(Object message) {
//        if (message instanceof AlertExecutor.SendTo) {
//            final AlertExecutor.SendTo msg = (AlertExecutor.SendTo) message;
//            return msg.getPayload();
//        }
//        return super.entityMessage(message);
//    }

    private static final int NUM_SHARDS = 1000;
}
