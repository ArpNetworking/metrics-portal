/*
 * Copyright 2015 Groupon.com
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
import com.arpnetworking.metrics.portal.expressions.ExpressionRepository;
import com.arpnetworking.metrics.portal.organizations.OrganizationProvider;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Maps;
import com.google.common.net.HttpHeaders;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import models.internal.Expression;
import models.internal.ExpressionQuery;
import models.internal.QueryResult;
import models.internal.impl.DefaultExpression;
import models.view.PagedContainer;
import models.view.Pagination;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.inject.Singleton;

/**
 * Metrics portal expression controller. Exposes APIs to query and manipulate expressions.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
@Singleton
public class ExpressionController extends Controller {

    /**
     * Public constructor.
     *
     * @param configuration Instance of Play's {@link Config}.
     * @param expressionRepository Instance of {@link ExpressionRepository}.
     * @param organizationProvider Instance of {@link OrganizationProvider}.
     */
    @Inject
    public ExpressionController(
            final Config configuration,
            final ExpressionRepository expressionRepository,
            final OrganizationProvider organizationProvider) {
        this(configuration.getInt("expression.limit"), expressionRepository, organizationProvider);
    }

    /**
     * Adds an expression in the expression repository.
     *
     * @return Ok if the alert was created or updated successfully, a failure HTTP status code otherwise.
     */
    public Result addOrUpdate() {
        final Expression expression;
        try {
            final models.view.Expression viewExpression = buildViewExpression(request().body());
            expression = convertToInternalExpression(viewExpression);
        } catch (final IOException e) {
            LOGGER.error()
                    .setMessage("Failed to build an expression.")
                    .setThrowable(e)
                    .log();
            return badRequest("Invalid request body.");
        }

        try {
            _expressionRepository.addOrUpdateExpression(expression, _organizationProvider.getOrganization(request()));
            // CHECKSTYLE.OFF: IllegalCatch - Convert any exception to 500
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            LOGGER.error()
                    .setMessage("Failed to add an expression.")
                    .setThrowable(e)
                    .log();
            return internalServerError();
        }
        return noContent();
    }

    /**
     * Query for expressions.
     *
     * @param contains The text to search for. Optional.
     * @param cluster The cluster of the statistic to evaluate as part of the exression. Optional.
     * @param service The service of the statistic to evaluate as part of the expression. Optional.
     * @param limit The maximum number of results to return. Optional.
     * @param offset The number of results to skip. Optional.
     * @return <code>Result</code> paginated matching expressions.
     */
    public Result query(
            final String contains,
            final String cluster,
            final String service,
            final Integer limit,
            final Integer offset) {

        // Convert and validate parameters
        final Optional<String> argContains = Optional.ofNullable(contains);
        final Optional<String> argCluster = Optional.ofNullable(cluster);
        final Optional<String> argService = Optional.ofNullable(service);
        final Optional<Integer> argOffset = Optional.ofNullable(offset);
        final int argLimit = Math.min(_maxLimit, Optional.of(MoreObjects.firstNonNull(limit, _maxLimit)).get());
        if (argLimit < 0) {
            return badRequest("Invalid limit; must be greater than or equal to 0");
        }
        if (argOffset.isPresent() && argOffset.get() < 0) {
            return badRequest("Invalid offset; must be greater than or equal to 0");
        }

        // Build conditions map
        final Map<String, String> conditions = Maps.newHashMap();
        if (argContains.isPresent()) {
            conditions.put("contains", argContains.get());
        }
        if (argCluster.isPresent()) {
            conditions.put("cluster", argCluster.get());
        }
        if (argService.isPresent()) {
            conditions.put("service", argService.get());
        }

        // Build a host repository query
        final ExpressionQuery query = _expressionRepository.createQuery(_organizationProvider.getOrganization(request()))
                .contains(argContains)
                .service(argService)
                .cluster(argCluster)
                .limit(argLimit)
                .offset(argOffset);

        // Execute the query
        final QueryResult<Expression> result;
        try {
            result = query.execute();
            // CHECKSTYLE.OFF: IllegalCatch - Convert any exception to 500
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            LOGGER.error()
                    .setMessage("Expression query failed")
                    .setThrowable(e)
                    .log();
            return internalServerError();
        }

        // Wrap the query results and return as JSON
        if (result.etag().isPresent()) {
            response().setHeader(HttpHeaders.ETAG, result.etag().get());
        }
        return ok(Json.toJson(new PagedContainer<>(
                result.values()
                        .stream()
                        .map(this::internalModelToViewModel)
                        .collect(Collectors.toList()),
                new Pagination(
                        request().path(),
                        result.total(),
                        result.values().size(),
                        argLimit,
                        argOffset,
                        conditions))));
    }

    private models.view.Expression buildViewExpression(final Http.RequestBody body) throws IOException {
        final JsonNode jsonBody = body.asJson();
        if (jsonBody == null) {
            throw new IOException();
        }
        return OBJECT_MAPPER.readValue(jsonBody.toString(), models.view.Expression.class);
    }

    private Expression convertToInternalExpression(final models.view.Expression viewExpression) throws IOException {
        try {
            return new DefaultExpression.Builder()
                    .setId(viewExpression.getId() == null ? null : UUID.fromString(viewExpression.getId()))
                    .setCluster(viewExpression.getCluster())
                    .setMetric(viewExpression.getMetric())
                    .setService(viewExpression.getService())
                    .setScript(viewExpression.getScript())
                    .build();
            // CHECKSTYLE.OFF: IllegalCatch - Translate any failure to bad input.
        } catch (final RuntimeException e) {
            // CHECKSTYLE.ON: IllegalCatch
            throw new IOException(e);
        }
    }

    private models.view.Expression internalModelToViewModel(final Expression expression) {
        final models.view.Expression viewExpression = new models.view.Expression();
        viewExpression.setCluster(expression.getCluster());
        viewExpression.setId(expression.getId().toString());
        viewExpression.setMetric(expression.getMetric());
        viewExpression.setScript(expression.getScript());
        viewExpression.setService(expression.getService());
        return viewExpression;
    }

    /**
     * Get specific expression.
     *
     * @param id The identifier of the expression.
     * @return Matching expression.
     */
    public Result get(final String id) {
        final UUID identifier;
        try {
            identifier = UUID.fromString(id);
        } catch (final IllegalArgumentException e) {
            return badRequest();
        }
        final Optional<Expression> result = _expressionRepository.get(identifier, _organizationProvider.getOrganization(request()));
        if (!result.isPresent()) {
            return notFound();
        }
        // Return as JSON
        return ok(Json.toJson(result.get()));
    }

    private ExpressionController(
            final int maxLimit,
            final ExpressionRepository expressionRepository,
            final OrganizationProvider organizationProvider) {
        _maxLimit = maxLimit;
        _expressionRepository = expressionRepository;
        _organizationProvider = organizationProvider;
    }

    private final int _maxLimit;
    private final ExpressionRepository _expressionRepository;
    private final OrganizationProvider _organizationProvider;

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpressionController.class);
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
}
