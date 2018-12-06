/*
 * Copyright 2018 Smartsheet
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

import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTime;

import java.util.Optional;

/**
 * Represents a time when an alert triggers.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public interface AlertTrigger {
    /**
     * The start time of the trigger.
     *
     * @return the start time
     */
    DateTime getTime();

    /**
     * The end time of the trigger.
     *
     * @return the end time; if indeterminate, {@code Optional.empty()}
     */
    Optional<DateTime> getEndTime();

    /**
     * The arguments (tags) for the trigger.
     *
     * @return the args
     */
    ImmutableMap<String, String> getArgs();

    /**
     * The group by (tags) for the trigger. These determine uniqueness for a trigger.
     *
     * @return the args
     */
    ImmutableMap<String, String> getGroupBy();

    /**
     * The message that describes the alert.
     *
     * @return description of the alert
     */
    String getMessage();
}
