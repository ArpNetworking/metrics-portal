/*
 * Copyright 2016 Inscope Metrics, Inc.
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

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import models.internal.Features;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Metrics portal application Play controller.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
@Singleton
public final class ApplicationController extends Controller {

    /**
     * Public constructor.
     *
     * @param features The {@link Features} instance.
     */
    @Inject
    public ApplicationController(final Features features) {
        _features = features;
        _featuresJson = Suppliers.memoize(() -> {
            try {
                return OBJECT_MAPPER.writeValueAsString(_features);
            } catch (final JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Render configure javascript.
     *
     * @return Rendered configure javascript.
     */
    public CompletionStage<Result> getConfigureJs() {
        return CompletableFuture.completedFuture(
                ok(views.html
                        .ConfigureJsViewModel.render(_featuresJson.get()))
                        .as("text/javascript"));
    }

    /**
     * Render the header view.
     *
     * @return Rendered header view.
     */
    public CompletionStage<Result> getHeaderViewModel() {
        return CompletableFuture.completedFuture(ok(views.html.HeaderViewModel.render(_features)));
    }

    private final Features _features;
    private final Supplier<String> _featuresJson;

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
}
