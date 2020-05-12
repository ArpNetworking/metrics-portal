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
import akka.pattern.Patterns;
import com.arpnetworking.rollups.CollectionActor;
import com.arpnetworking.rollups.ConsistencyChecker;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
     *
     * @param mapper an {@link ObjectMapper} to use to deserialize requests
     * @param consistencyCheckerQueue the {@link CollectionActor} to submit {@link ConsistencyChecker.Task}s to
     */
    @Inject
    public RollupController(
            final ObjectMapper mapper,
            @Named("RollupConsistencyCheckerQueue") final ActorRef consistencyCheckerQueue
    ) {
        _mapper = mapper;
        _consistencyCheckerQueue = consistencyCheckerQueue;
    }

    /**
     * Requests a rollup be consistency-checked.
     *
     * @return Ok
     */
    public CompletionStage<Result> enqueueConsistencyCheck() {
        final ConsistencyChecker.Task task;
        try {
            task = _mapper.treeToValue(request().body().asJson(), ConsistencyChecker.Task.class);
        } catch (final IOException err) {
            return CompletableFuture.completedFuture(badRequest(err.getMessage()));
        }

        final CompletableFuture<Result> result = new CompletableFuture<>();
        Patterns.ask(_consistencyCheckerQueue, new CollectionActor.Add<>(task), Duration.ofSeconds(10)).whenComplete((response, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
                return;
            }
            if (response instanceof CollectionActor.AddAccepted) {
                LOGGER.info()
                        .setMessage("submitted consistency-checker task")
                        .addData("task", task)
                        .log();
                result.complete(noContent());
            } else if (response instanceof CollectionActor.AddRejected) {
                LOGGER.warn()
                        .setMessage("consistency-check task rejected")
                        .addData("task", task)
                        .log();
                result.complete(status(503, "consistency-checkers are too busy right now"));
            } else {
                LOGGER.error()
                        .setMessage("unexpected response from consistency-checker queue")
                        .addData("task", task)
                        .addData("response", response)
                        .log();
                result.complete(internalServerError("unexpected response from consistency-checker queue"));
            }
        });

        return result;
    }

    private final ActorRef _consistencyCheckerQueue;
    private final ObjectMapper _mapper;

    private static final Logger LOGGER = LoggerFactory.getLogger(RollupController.class);
}
