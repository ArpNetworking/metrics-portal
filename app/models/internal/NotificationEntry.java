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
package models.internal;

import com.arpnetworking.mql.grammar.AlertTrigger;
import com.google.inject.Injector;

import java.util.concurrent.CompletionStage;

/**
 * A way to notify.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public interface NotificationEntry {
    /**
     * Dispatches notifications to the recipient.
     *
     * @param alert the alert that triggered
     * @param trigger the trigger of the notification
     * @param injector injector to create dependencies
     * @return a {@link CompletionStage} indicating completion
     */
    CompletionStage<Void> notifyRecipient(Alert alert, AlertTrigger trigger, Injector injector);

    /**
     * Converts the model to a view model.
     *
     * @return a new view model
     */
    models.view.NotificationEntry toView();
}
