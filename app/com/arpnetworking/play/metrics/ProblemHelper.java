/*
 * Copyright 2019 Dropbox, Inc.
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
package com.arpnetworking.play.metrics;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import models.internal.Problem;
import play.Environment;
import play.libs.Json;
import play.mvc.Http;

import java.util.List;

/**
 * Utilities for sending {@link Problem}-related information to clients.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class ProblemHelper {
    /**
     * Render a {@link Problem} as JSON.
     *
     * @param env The current Play environment.
     * @param ex The exception causing the problem.
     * @param problemCode The problem code.
     *
     * @return A JSON representation of the problem.
     */
    public static ObjectNode createErrorJson(final Environment env, final Exception ex, final String problemCode) {
        final ObjectNode errorNode = createErrorJson(ImmutableList.of(new Problem.Builder().setProblemCode(problemCode).build()));
        if (env.isDev()) {
            if (ex.getMessage() != null) {
                errorNode.put("details", ex.getMessage());
            } else {
                errorNode.put("details", ex.toString());
            }
        }
        return errorNode;
    }

    /**
     * Render a {@link Problem} as JSON.
     *
     * @param problem The problem code.
     *
     * @return A JSON representation of the problem.
     */
    public static ObjectNode createErrorJson(final Problem problem) {
        return createErrorJson(ImmutableList.of(problem));
    }

    /**
     * Render several {@link Problem}s as JSON.
     *
     * @param problems The problems.
     *
     * @return A JSON representation of the problems.
     */
    public static ObjectNode createErrorJson(final List<Problem> problems) {
        final ObjectNode errorJson = Json.newObject();
        final ArrayNode errors = errorJson.putArray("errors");
        problems.forEach(problem -> errors.add(translate(problem)));
        return errorJson;
    }

    /**
     * Translate a problem into a given locale.
     *
     * @param problem The problem to translate.
     * @return The translated problem.
     */
    public static String translate(final Problem problem) {
        return Http.Context.current().messages().at(problem.getProblemCode(), problem.getArgs().toArray());
    }

    private ProblemHelper() {}
}
