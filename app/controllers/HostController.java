/*
 * Copyright 2014 Groupon.com
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
import com.arpnetworking.metrics.portal.hosts.HostRepository;
import com.arpnetworking.metrics.portal.organizations.OrganizationRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Maps;
import com.google.common.net.HttpHeaders;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import models.internal.Host;
import models.internal.HostQuery;
import models.internal.MetricsSoftwareState;
import models.internal.QueryResult;
import models.internal.impl.DefaultHost;
import models.view.PagedContainer;
import models.view.Pagination;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;

/**
 * Metrics portal host controller. Exposes APIs to query and manipulate hosts.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
@Singleton
public class HostController extends Controller {

    /**
     * Public constructor.
     *
     * @param configuration Instance of Play's {@link Config}
     * @param hostRepository Instance of {@link HostRepository}
     * @param organizationRepository Instance of {@link OrganizationRepository}.
     */
    @Inject
    public HostController(
            final Config configuration,
            final HostRepository hostRepository,
            final OrganizationRepository organizationRepository) {
        this(configuration.getInt("hosts.limit"), hostRepository, organizationRepository);
    }

    /**
     * Get specific host by hostname.
     *
     * @param id The hostname to retrieve.
     * @return Matching host.
     */
    public Result get(final String id) {
        final Optional<Host> result = _hostRepository.getHost(id, _organizationRepository.get(request()));
        if (!result.isPresent()) {
            return notFound();
        }
        // Return as JSON
        return ok(Json.toJson(result.map(this::internalModelToViewModel)));
    }

    /**
     * Adds or updates a host in the host repository.
     *
     * @return Ok if the host was created or updated successfully, a failure HTTP status code otherwise.
     */
    public Result addOrUpdate() {
        final Host host;
        try {
            final models.view.Host viewHost = buildViewHost(request().body());
            host = convertToInternalHost(viewHost);
        } catch (final IOException e) {
            LOGGER.error()
                    .setMessage("Failed to build a host.")
                    .setThrowable(e)
                    .log();
            return badRequest("Invalid request body.");
        }

        try {
            _hostRepository.addOrUpdateHost(host, _organizationRepository.get(request()));
            // CHECKSTYLE.OFF: IllegalCatch - Convert any exception to 500
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            LOGGER.error()
                    .setMessage("Failed to add a host.")
                    .setThrowable(e)
                    .log();
            return internalServerError();
        }
        return noContent();
    }

    /**
     * Query for hosts.
     *
     * @param name The complete or partial name of the host. Optional.
     * @param state The state of the metrics software on the host. Optional.
     * @param cluster The name of the cluster for the host. Optional.
     * @param limit The maximum number of results to return. Optional.
     * @param offset The number of results to skip. Optional.
     * @param sort_by The field to sort results by. Optional.
     * @return <code>Result</code> paginated matching hosts.
     */
    // CHECKSTYLE.OFF: ParameterNameCheck - Names must match query parameters.
    public Result query(
            final String name,
            final String state,
            final String cluster,
            final Integer limit,
            final Integer offset,
            final String sort_by) {
        // CHECKSTYLE.ON: ParameterNameCheck

        // Convert and validate parameters
        final MetricsSoftwareState stateValue;
        try {
            stateValue = state == null ? null : MetricsSoftwareState.valueOf(state);
        } catch (final IllegalArgumentException iae) {
            return badRequest("Invalid state argument");
        }
        final HostQuery.Field sortByValue;
        try {
            sortByValue = sort_by == null ? null : HostQuery.Field.valueOf(sort_by);
        } catch (final IllegalArgumentException iae) {
            return badRequest("Invalid sort_by argument");
        }
        final Optional<String> argName = Optional.ofNullable(name);
        final Optional<MetricsSoftwareState> argState = Optional.ofNullable(stateValue);
        final Optional<String> argCluster = Optional.ofNullable(cluster);
        final Optional<Integer> argOffset = Optional.ofNullable(offset);
        final Optional<HostQuery.Field> argSortBy = Optional.ofNullable(sortByValue);
        final int argLimit = Math.min(_maxLimit, Optional.of(MoreObjects.firstNonNull(limit, _maxLimit)).get());
        if (argLimit < 0) {
            return badRequest("Invalid limit; must be greater than or equal to 0");
        }
        if (argOffset.isPresent() && argOffset.get() < 0) {
            return badRequest("Invalid offset; must be greater than or equal to 0");
        }

        // Build conditions map
        final Map<String, String> conditions = Maps.newHashMap();
        if (argName.isPresent()) {
            conditions.put("name", argName.get());
        }
        if (argState.isPresent()) {
            conditions.put("state", argState.get().toString());
        }
        if (argCluster.isPresent()) {
            conditions.put("cluster", argCluster.get());
        }
        if (argSortBy.isPresent()) {
            conditions.put("sort_by", argSortBy.get().toString());
        }

        // Build a host repository query
        final HostQuery query = _hostRepository.createHostQuery(_organizationRepository.get(request()))
                .partialHostname(argName)
                .metricsSoftwareState(argState)
                .cluster(argCluster)
                .limit(argLimit)
                .offset(argOffset)
                .sortBy(argSortBy);

        // Execute the query
        return executeQuery(argOffset, argLimit, conditions, query);
    }

    private Result executeQuery(
            final Optional<Integer> argOffset,
            final int argLimit,
            final Map<String, String> conditions,
            final HostQuery query) {

        final QueryResult<Host> result;
        try {
            result = _hostRepository.queryHosts(query);
            // CHECKSTYLE.OFF: IllegalCatch - Convert any exception to 500
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            LOGGER.error()
                    .setMessage("Host query failed")
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

    private models.view.Host internalModelToViewModel(final Host host) {
        final models.view.Host viewHost = new models.view.Host();
        viewHost.setCluster(host.getCluster().orElse(null));
        viewHost.setHostname(host.getHostname());
        viewHost.setMetricsSoftwareState(host.getMetricsSoftwareState().toString());
        return viewHost;
    }

    private Host convertToInternalHost(final models.view.Host viewHost) throws IOException {
        try {
            final DefaultHost.Builder hostBuilder = new DefaultHost.Builder()
                    .setCluster(viewHost.getCluster())
                    .setHostname(viewHost.getHostname());
            if (viewHost.getMetricsSoftwareState() != null) {
                hostBuilder.setMetricsSoftwareState(MetricsSoftwareState.valueOf(viewHost.getMetricsSoftwareState()));
            }
            return hostBuilder.build();
            // CHECKSTYLE.OFF: IllegalCatch - Translate any failure to bad input.
        } catch (final RuntimeException e) {
            // CHECKSTYLE.ON: IllegalCatch
            throw new IOException(e);
        }
    }

    private models.view.Host buildViewHost(final Http.RequestBody body) throws IOException {
        final JsonNode jsonBody = body.asJson();
        if (jsonBody == null) {
            throw new IOException();
        }
        return OBJECT_MAPPER.readValue(jsonBody.toString(), models.view.Host.class);
    }

    private HostController(final int maxLimit, final HostRepository hostRepository, final OrganizationRepository organizationRepository) {
        _maxLimit = maxLimit;
        _hostRepository = hostRepository;
        _organizationRepository = organizationRepository;
    }

    private final int _maxLimit;
    private final HostRepository _hostRepository;
    private final OrganizationRepository _organizationRepository;

    private static final Logger LOGGER = LoggerFactory.getLogger(HostController.class);
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
}
