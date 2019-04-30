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
package controllers;

import com.arpnetworking.commons.jackson.databind.ObjectMapperFactory;
import com.arpnetworking.metrics.portal.organizations.OrganizationRepository;
import com.arpnetworking.metrics.portal.reports.ReportRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import models.view.PagedContainer;
import models.view.Pagination;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Singleton;

/**
 * Metrics portal report controller. Exposes APIs to query and manipulate reports.
 *
 * @author Christian Briones (cbriones at dropbox dot com).
 */
@Singleton
public class ReportController extends Controller {

    /**
     * Public constructor.
     *
     * @param configuration Instance of Play's {@code Config}.
     * @param reportRepository Instance of {@link ReportRepository}.
     * @param organizationRepository Instance of {@link OrganizationRepository}.
     */
    @Inject
    public ReportController(
            final Config configuration,
            final ReportRepository reportRepository,
            final OrganizationRepository organizationRepository) {
        this(configuration.getInt("reports.limit"), reportRepository, organizationRepository);
    }

    /**
     * Adds a report to the report repository.
     *
     * @return Ok if the report was added or updated successfully, an HTTP error code otherwise.
     */
    public Result addOrUpdate() {
        return noContent();
    }

    /**
     * Query for reports.
     *
     * @param limit  The maximum number of results to return. Optional.
     * @param offset The number of results to skip. Optional.
     * @return {@link Result} paginated matching reports.
     */
    // CHECKSTYLE.OFF: ParameterNameCheck - Names must match query parameters.
    public Result query(
            @Nullable final Integer limit,
            @Nullable final Integer offset) {
        // CHECKSTYLE.ON: ParameterNameCheck

        final Map<String, String> conditions = ImmutableMap.of();

        // Wrap the query results and return as JSON
        return ok(Json.toJson(new PagedContainer<>(Collections.emptyList(),
                new Pagination(
                        request().path(),
                        0,
                        0,
                        0,
                        Optional.of(0),
                        conditions))));
    }

    /**
     * Get specific report.
     *
     * @param id The identifier of the report.
     * @return Matching report.
     */
    public Result get(final String id) {
        return notFound();
    }

    /**
     * Delete a specific report.
     *
     * @param id The identifier of the report.
     * @return No content if successful, otherwise an HTTP error code.
     */
    public Result delete(final String id) {
        return notFound();
    }

    private ReportController(
            final int maxLimit,
            final ReportRepository reportRepository,
            final OrganizationRepository organizationRepository) {
        _maxLimit = maxLimit;
        _reportRepository = reportRepository;
        _organizationRepository = organizationRepository;
    }

    private final int _maxLimit;
    private final ReportRepository _reportRepository;
    private final OrganizationRepository _organizationRepository;

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportController.class);
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
}
