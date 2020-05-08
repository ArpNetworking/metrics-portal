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

package com.arpnetworking.metrics.portal.alerts;

import com.arpnetworking.metrics.portal.scheduling.JobExecutionRepository;
import models.internal.AlertEvaluationResult;
import models.internal.scheduling.JobExecution;

/**
 * A repository for storing and retrieving {@link JobExecution}s for an alert.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 *
 * @apiNote
 * This class is intended for use as a type-token so that Guice can reflectively instantiate
 * the {@code JobExecutionRepository} at runtime. Scheduling code should be using a generic {@code JobExecutionRepository}.
 */
public interface AlertExecutionRepository extends JobExecutionRepository<AlertEvaluationResult> {
}

