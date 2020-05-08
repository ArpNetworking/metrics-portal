/*
 * Copyright 2020 Dropbox
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
package controllers;

import akka.actor.ActorRef;
import com.arpnetworking.rollups.ConsistencyChecker;
import com.arpnetworking.rollups.QueueActor;
import com.arpnetworking.rollups.RollupPeriod;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.inject.Inject;
import play.mvc.Controller;
import play.mvc.Result;

import java.time.Instant;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Metrics portal rollup controller. Exposes APIs to query and manipulate rollups.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
@Singleton
public class RollupController extends Controller {

    /**
     * Public constructor.
     */
    @Inject
    public RollupController(
        @Named("RollupConsistencyCheckerQueue") final ActorRef consistencyCheckerQueue
    ) {
        _consistencyCheckerQueue = consistencyCheckerQueue;
    }

    /**
     * Requests a rollup be consistency-checked.
     *
     * @return Ok
     */
    public Result enqueueConsistencyCheck(
            final String sourceMetricName,
            final String rollupMetricName,
            final String period,
            final String startTime
    ) {
        final ConsistencyChecker.Task task = new ConsistencyChecker.Task.Builder()
                .setSourceMetricName(sourceMetricName)
                .setRollupMetricName(rollupMetricName)
                .setPeriod(RollupPeriod.valueOf(period))
                .setStartTime(Instant.parse(startTime))
                .setTrigger(ConsistencyChecker.Task.Trigger.HUMAN_REQUESTED)
                .build();
        _consistencyCheckerQueue.tell(
                new QueueActor.Add<>(task),
                null
        );
        LOGGER.info()
                .setMessage("submitted consistency-checker task")
                .addData("task", task)
                .log();
        return noContent();
    }

    private final ActorRef _consistencyCheckerQueue;

    private static final Logger LOGGER = LoggerFactory.getLogger(RollupController.class);
}
