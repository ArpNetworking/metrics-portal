/**
 * Copyright 2016 Groupon.com
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
package com.arpnetworking.database.h2.triggers;

import models.ebean.AlertEtags;

/**
 * Trigger to update Etag after every insert, delete or update statement.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
public class AlertsUpdateEtagTrigger extends BaseUpdateEtagTrigger {

    /**
     * Public no args constructor.
     */
    public AlertsUpdateEtagTrigger() {
        super(AlertEtags::incrementEtag, 7);
    }
}
