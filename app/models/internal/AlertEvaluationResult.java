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

package models.internal;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import models.internal.impl.DefaultAlertEvaluationResult;
import models.internal.scheduling.JobExecution;

/**
 * The result of evaluating an Alert.
 * <p>
 * This is not useful on its own since it does not expose any event timestamps or alert metadata.
 * It's expected that those values will be obtained from the associated {@link JobExecution} instances along with the alert definition itself.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "@name"
)
@JsonSubTypes(
        @JsonSubTypes.Type(
                value = DefaultAlertEvaluationResult.class,
                name = "DefaultAlertEvaluationResult"
        )
)
public interface AlertEvaluationResult {

    // XXX(cbriones): This is likely to change once we actually integrate alerts with the job scheduling code.

    /**
     * A list of firing tag-sets at the time of evaluation.
     *
     * @return the tag sets
     */
    ImmutableList<ImmutableMap<String, String>> getFiringTags();
}
