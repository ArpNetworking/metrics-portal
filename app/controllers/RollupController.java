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

import com.arpnetworking.rollups.ConsistencyChecker;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.pattern.Patterns;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

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
     * @param consistencyChecker the {@link ConsistencyChecker} to submit {@link ConsistencyChecker.Task}s to
     */
    @Inject
    public RollupController(
            final ObjectMapper mapper,
            @Named("RollupConsistencyChecker") final ActorRef consistencyChecker
    ) {
        _mapper = mapper;
        _consistencyChecker = consistencyChecker;
    }

    /**
     * Requests a rollup be consistency-checked.
     *
     * This endpoint is currently intended only for debugging purposes. Do not rely on it.
     *
     * @param request Http.Request being handled.
     *
     * @return 204 if the consistency-check task was successfully enqueued; else 503 if the queue is full; else 500 for unknown failures.
     */
    public CompletionStage<Result> enqueueConsistencyCheck(final Http.Request request) {
        final ConsistencyChecker.Task task;
        try {
            task = _mapper.treeToValue(request.body().asJson(), ConsistencyChecker.Task.class);
        } catch (final IOException err) {
            return CompletableFuture.completedFuture(badRequest(err.getMessage()));
        }

        return Patterns.ask(_consistencyChecker, task, Duration.ofSeconds(10))
                .handle((response, error) -> {
                    if (error == null) {
                        LOGGER.info()
                                .setMessage("submitted consistency-checker task")
                                .addData("task", task)
                                .log();
                        return noContent();
                    } else if (error instanceof ConsistencyChecker.BufferFull) {
                        LOGGER.warn()
                                .setMessage("consistency-checker queue rejected task")
                                .addData("task", task)
                                .setThrowable(error)
                                .log();
                        return status(503, "consistency-checkers are too busy right now");
                    } else {
                        throw new CompletionException(error);
                    }
                });
    }

    private final ActorRef _consistencyChecker;
    private final ObjectMapper _mapper;

    private static final Logger LOGGER = LoggerFactory.getLogger(RollupController.class);
}
