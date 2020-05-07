/*
 * Copyright 2020 Dropbox, Inc.
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

package models.internal.alerts;

import com.google.common.collect.ImmutableMap;

/**
 * @author Christian Briones (cbriones at dropbox dot com)
 */
public interface FiringAlertResult {
    /**
     * The arguments (tags) for the alert.
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
