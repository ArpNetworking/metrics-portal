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
import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.metrics.portal.notifications.NotificationRepository;
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
import models.internal.Alert;
import models.internal.AlertQuery;
import models.internal.Context;
import models.internal.Organization;
import models.internal.QueryResult;
import models.view.PagedContainer;
import models.view.Pagination;
import net.sf.oval.exception.ConstraintsViolatedException;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Singleton;

/**
 * Metrics portal alert controller. Exposes APIs to query and manipulate alerts.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
@Singleton
public class AlertController extends Controller {

    /**
     * Public constructor.
     *
     * @param configuration Instance of Play's {@link Config}.
     * @param alertRepository Instance of {@link AlertRepository}.
     * @param organizationProvider Instance of {@link OrganizationProvider}.
     * @param notificationRepository Instance of <code>NotificationRepository</code>
     */
    @Inject
    public AlertController(
            final Config configuration,
            final AlertRepository alertRepository,
            final OrganizationProvider organizationProvider,
            final NotificationRepository notificationRepository) {
        this(configuration.getInt("alerts.limit"), alertRepository, organizationProvider, notificationRepository);
    }

    /**
     * Adds an alert in the alert repository.
     *
     * @return Ok if the alert was created or updated successfully, a failure HTTP status code otherwise.
     */
    public Result addOrUpdate() {
        final Alert alert;
        final Organization organization = _organizationProvider.getOrganization(request());
        try {
            final JsonNode jsonBody = request().body().asJson();
            if (jsonBody == null) {
                return badRequest("empty request body.");
            }
            final models.view.Alert viewAlert = OBJECT_MAPPER.treeToValue(jsonBody, models.view.Alert.class);
            alert = viewAlert.toInternal(organization, _notificationRepository, OBJECT_MAPPER);
        } catch (final IOException | ConstraintsViolatedException e) {
            LOGGER.error()
                    .setMessage("Failed to build an alert.")
                    .setThrowable(e)
                    .log();
            return badRequest("Invalid request body.");
        }

        try {
            _alertRepository.addOrUpdateAlert(alert, organization);
            // CHECKSTYLE.OFF: IllegalCatch - Convert any exception to 500
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            LOGGER.error()
                    .setMessage("Failed to add an alert.")
                    .setThrowable(e)
                    .log();
            return internalServerError();
        }
        return noContent();
    }

    /**
     * Query for alerts.
     *
     * @param contains The text to search for. Optional.
     * @param context The context of the alert. Optional.
     * @param cluster The cluster of the statistic to evaluate as part of the alert. Optional.
     * @param service The service of the statistic to evaluate as part of the alert. Optional.
     * @param limit The maximum number of results to return. Optional.
     * @param offset The number of results to skip. Optional.
     * @return <code>Result</code> paginated matching alerts.
     */
    // CHECKSTYLE.OFF: ParameterNameCheck - Names must match query parameters.
    public Result query(
            @Nullable final String contains,
            @Nullable final String context,
            @Nullable final String cluster,
            @Nullable final String service,
            @Nullable final Integer limit,
            @Nullable final Integer offset) {
        // CHECKSTYLE.ON: ParameterNameCheck

        // Convert and validate parameters
        final Optional<String> argContains = Optional.ofNullable(contains);
        final Context contextValue;
        try {
            contextValue = context == null ? null : Context.valueOf(context);
        } catch (final IllegalArgumentException iae) {
            return badRequest("Invalid context argument");
        }
        final Optional<Context> argContext = Optional.ofNullable(contextValue);
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
        if (argContext.isPresent()) {
            conditions.put("context", argContext.get().toString());
        }
        if (argCluster.isPresent()) {
            conditions.put("cluster", argCluster.get());
        }
        if (argService.isPresent()) {
            conditions.put("service", argService.get());
        }

        // Build a host repository query
        final AlertQuery query = _alertRepository.createQuery(_organizationProvider.getOrganization(request()))
                .contains(argContains)
                .limit(argLimit)
                .offset(argOffset);

        // Execute the query
        final QueryResult<Alert> result;
        try {
            result = query.execute();
            // CHECKSTYLE.OFF: IllegalCatch - Convert any exception to 500
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            LOGGER.error()
                    .setMessage("Alert query failed")
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
                        .map(models.view.Alert::fromInternal)
                        .collect(Collectors.toList()),
                new Pagination(
                        request().path(),
                        result.total(),
                        result.values().size(),
                        argLimit,
                        argOffset,
                        conditions))));
    }

    /**
     * Get specific alert.
     *
     * @param id The identifier of the alert.
     * @return Matching alert.
     */
    public Result get(final String id) {
        final UUID identifier;
        try {
            identifier = UUID.fromString(id);
        } catch (final IllegalArgumentException e) {
            return badRequest();
        }
        final Optional<Alert> result = _alertRepository.get(identifier, _organizationProvider.getOrganization(request()));
        if (!result.isPresent()) {
            return notFound();
        }
        // Return as JSON
        return ok(Json.toJson(models.view.Alert.fromInternal(result.get())));
    }

    /**
     * Delete a specific alert.
     *
     * @param id The identifier of the alert.
     * @return No content
     */
    public Result delete(final String id) {
        final UUID identifier = UUID.fromString(id);
        final int deleted = _alertRepository.delete(identifier, _organizationProvider.getOrganization(request()));
        if (deleted > 0) {
            return noContent();
        } else {
            return notFound();
        }
    }

    private AlertController(
            final int maxLimit,
            final AlertRepository alertRepository,
            final OrganizationProvider organizationProvider,
            final NotificationRepository notificationRepository) {
        _maxLimit = maxLimit;
        _alertRepository = alertRepository;
        _organizationProvider = organizationProvider;
        _notificationRepository = notificationRepository;
    }

    private final int _maxLimit;
    private final AlertRepository _alertRepository;
    private final OrganizationProvider _organizationProvider;
    private final NotificationRepository _notificationRepository;

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertController.class);
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
}
