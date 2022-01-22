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
import com.google.inject.Inject;
import models.internal.Problem;
import play.Environment;
import play.i18n.Lang;
import play.i18n.MessagesApi;
import play.libs.Json;

import java.util.List;
import java.util.Optional;

/**
 * Utilities for sending {@link Problem}-related information to clients.
 *
 * @author Spencer Pearson (spencerpearson at dropbox dot com)
 */
public final class ProblemHelper {

    @Inject
    public ProblemHelper(final MessagesApi messagesApi) {
        _messagesApi = messagesApi;
    }
    /**
     * Render a {@link Problem} as JSON.
     *
     * @param env The current Play environment.
     * @param ex The exception causing the problem.
     * @param problemCode The problem code.
     * @param lang The language to render the errors.
     *
     * @return A JSON representation of the problem.
     */
    public ObjectNode createErrorJson(final Environment env, final Exception ex, final String problemCode, final Optional<Lang> lang) {
        final ObjectNode errorNode = createErrorJson(ImmutableList.of(new Problem.Builder().setProblemCode(problemCode).build()), lang);
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
     * @param lang The language to render the errors.
     *
     * @return A JSON representation of the problem.
     */
    public ObjectNode createErrorJson(final Problem problem, final Optional<Lang> lang) {
        return createErrorJson(ImmutableList.of(problem), lang);
    }

    /**
     * Render several {@link Problem}s as JSON.
     *
     * @param problems The problems.
     * @param lang Language to render problems in
     *
     * @return A JSON representation of the problems.
     */
    public ObjectNode createErrorJson(final List<Problem> problems, final Optional<Lang> lang) {
        final ObjectNode errorJson = Json.newObject();
        final ArrayNode errors = errorJson.putArray("errors");
        problems.forEach(problem -> errors.add(translate(problem, lang)));
        return errorJson;
    }

    /**
     * Translate a problem into a given locale.
     *
     * @param problem The problem to translate.
     * @param lang Language to render problems in
     *
     * @return The translated problem.
     */
    public String translate(final Problem problem, final Optional<Lang> lang) {
        return _messagesApi.get(lang.orElse(Lang.defaultLang()), problem.getProblemCode(), problem.getArgs().toArray());
    }

    private final MessagesApi _messagesApi;
}
