/*
 * Copyright 2016 Groupon.com
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
import com.arpnetworking.metrics.portal.version_specifications.VersionSpecificationRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.typesafe.config.Config;
import models.internal.QueryResult;
import models.internal.VersionSetLookupResult;
import models.internal.VersionSpecificationAttribute;
import models.internal.impl.DefaultPackageVersion;
import models.internal.impl.DefaultVersionSet;
import models.internal.impl.DefaultVersionSpecification;
import models.internal.impl.DefaultVersionSpecificationAttribute;
import models.view.PackageVersion;
import models.view.PagedContainer;
import models.view.Pagination;
import models.view.VersionSet;
import models.view.VersionSpecification;
import models.view.VersionSpecificationDetails;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Singleton;

/**
 * Metrics portal version specifications controller. Exposes APIs to add, remove and query version specifications and
 * associated package-versions.
 *
 * @author Matthew Hayter (mhayter at groupon dot com)
 */
@Singleton
public class VersionSpecificationController extends Controller {

    /**
     * Public constructor.
     *
     * @param configuration Instance of Play's <code>Configuration</code>.
     * @param versionSpecificationRepository Instance of <code>AlertRepository</code>.
     */
    @Inject
    public VersionSpecificationController(
            final Config configuration,
            final VersionSpecificationRepository versionSpecificationRepository) {
        this(configuration.getInt("version_specifications.limit"), versionSpecificationRepository);
    }

    /**
     * The primary endpoint for Evergreen. Given a set of host attributes, this endpoint returns the
     * appropriate version set as determined by the set of <code>VersionSpecifications</code> and their
     * associated <code>VersionSets</code>. See the README.md for more information.
     *
     * @return <code>Result</code> VersionSet
     */
    public Result lookupHostVersionSet() {

        // If the queryString contains multiple values for a single parameter, select the last parameter.
        final Map<String, String> hostAttributes = selectLastQueryParams(request().queryString());

        Optional<Instant> ifModifiedSince = Optional.empty();
        if (request().hasHeader(IF_MODIFIED_SINCE)) {
            ifModifiedSince = request().header(IF_MODIFIED_SINCE).map(mod -> Instant.from(RFC_1123_DATE_TIME.parse(mod)));
        }

        final VersionSetLookupResult versionSetResult =
                _versionSpecificationRepository.lookupVersionSetByHostAttributes(hostAttributes, ifModifiedSince);

        if (versionSetResult.isNotFound()) {
            // This is an error in the Evergreen dataset: there should always be a default/fallback version set specified.
            LOGGER.error()
                    .setMessage("No VersionSet found for host attributes")
                    .addData("hostAttributes", hostAttributes)
                    .log();
            return internalServerError();
        } else if (versionSetResult.isNotModified()) {
            return status(304, "Not Modified");
        } else if (!versionSetResult.getVersionSet().isPresent()) {
            // Unknown result must have been added to VersionSetLookupResult
            LOGGER.error()
                    .setMessage("Unknown result from lookupVersionSetByHostAttributes()")
                    .log();
            return internalServerError();
        }
        response().setHeader(LAST_MODIFIED, RFC_1123_DATE_TIME.format(versionSetResult.getLastModified().get()));
        return ok(Json.toJson(internalToViewModel(versionSetResult.getVersionSet().get())));
    }

    /**
     * Get the <code>VersionSet</code> associated with the given UUID.
     *
     * @param uuid The UUID.
     * @return <code>Result</code> version set.
     */
    public Result getVersionSet(final UUID uuid) {
        final Optional<models.internal.VersionSet> maybeVersionSet = _versionSpecificationRepository.getVersionSet(uuid);
        if (!maybeVersionSet.isPresent()) {
            return notFound();
        }
        return ok(Json.toJson(internalToViewModel(maybeVersionSet.get())));
    }

    /**
     * Update or create a VersionSet with the given UUID.
     *
     * @param uuid The UUID.
     * @return <code>Result</code> status code indicating success or failure.
     */
    public Result updateCreateVersionSet(final UUID uuid) {
        final String bodyText = request().body().asText();

        final VersionSet view;
        try {
            view = OBJECT_MAPPER.readValue(bodyText, VersionSet.class);
        } catch (final IOException e) {
            LOGGER.error()
                    .setMessage("Failed to deserialize body in updateCreateVersionSet")
                    .addData("Body", bodyText)
                    .addData("Uuid", uuid)
                    .setThrowable(e)
                    .log();
            return badRequest("Malformed request body");
        }

        // Check that each `packages` key matches the respective packageName
        for (Map.Entry<String, PackageVersion> packageVersionEntry : view.getPackages().entrySet()) {
            if (!packageVersionEntry.getKey().equals(packageVersionEntry.getValue().getPackageName())) {
                return badRequest("versionSet.packages: keys must match package names.");
            }
        }
        final models.internal.VersionSet internalModel = viewToInternalVersionSpec(view);

        try {
            _versionSpecificationRepository.addOrUpdateVersionSet(internalModel);
            // CHECKSTYLE.OFF: IllegalCatch - Convert any exception to 500
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            LOGGER.error()
                    .setMessage("Failed to create / update VersionSpecification")
                    .addData("Models.view.VersionSet", view)
                    .addData("Models.internal.VersionSet", internalModel)
                    .setThrowable(e)
                    .log();
            return internalServerError();
        }
        
        return ok();
    }

    /**
     * Retrieve the paginated list of <code>VersionSpecifications</code>.
     *
     * @param offset The number of version specifications to skip in the response
     * @param limit The maximum number of version specifications to return.
     * @return <code>Result</code> paginated list of version specifications.
     */
    public Result listVersionSpecifications(@Nullable final Integer offset, @Nullable final Integer limit) {
        final int validOffset = Optional.ofNullable(offset).orElse(0);
        final int validLimit = Optional.ofNullable(limit).orElse(DEFAULT_MAX_LIMIT);
        final QueryResult<models.internal.VersionSpecification> versionSpecificationsQuery =
                _versionSpecificationRepository.getVersionSpecifications(validOffset, validLimit);
        final List<? extends models.internal.VersionSpecification> versionSpecifications = versionSpecificationsQuery.values();

        final List<VersionSpecificationDetails> views =
                versionSpecifications.stream().map(this::internalToViewModel).collect(Collectors.toList());

        final PagedContainer<VersionSpecificationDetails> pagedVersionSpecViews = new PagedContainer<>(
                views,
                new Pagination(
                        request().path(),
                        versionSpecificationsQuery.total(),
                        views.size(),
                        validLimit,
                        Optional.of(validOffset),
                        Collections.emptyMap()));
        return ok(Json.toJson(pagedVersionSpecViews));
    }

    /**
     * Update or create a <code>VersionSpecification</code> specified by the given UUID.
     *
     * @param uuid The UUID.
     * @return <code>Result</code> status code indicating success or failure.
     */
    public Result updateCreateVersionSpecification(final UUID uuid) {
        final String bodyText = request().body().asText();

        final VersionSpecification view;
        try {
            view = OBJECT_MAPPER.readValue(bodyText, VersionSpecification.class);
        } catch (final IOException e) {
            LOGGER.error()
                    .setMessage("Failed to deserialize body in updateCreateVersionSpecification")
                    .addData("Body", bodyText)
                    .addData("Uuid", uuid)
                    .setThrowable(e)
                    .log();
            return badRequest("Malformed request body");
        }

        final Optional<models.internal.VersionSet> versionSet = _versionSpecificationRepository.getVersionSet(view.getVersionSetId());
        if (!versionSet.isPresent()) {
            return notFound("Referenced version set not found");
        }
        final models.internal.VersionSpecification internalModel = viewToInternalVersionSpec(view, versionSet.get());

        try {
            _versionSpecificationRepository.addOrUpdateVersionSpecification(internalModel);
            // CHECKSTYLE.OFF: IllegalCatch - Convert any exception to 500
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            LOGGER.error()
                    .setMessage("Failed to create / update VersionSpecification")
                    .addData("models.view.VersionSpecification", view)
                    .addData("models.internal.VersionSpecification", internalModel)
                    .setThrowable(e)
                    .log();
            return internalServerError();
        }
        return ok();
    }

    /**
     * Get the <code>VersionSpecification</code> associated with the given UUID.
     *
     * @param uuid The UUID.
     * @return <code>Result</code> version specification.
     */
    public Result getVersionSpecification(final UUID uuid) {
        final Optional<models.internal.VersionSpecification> versionSpecification =
                _versionSpecificationRepository.getVersionSpecification(uuid);
        if (!versionSpecification.isPresent()) {
            return notFound();
        }

        return ok(Json.toJson(internalToViewModel(versionSpecification.get())));
    }

    /**
     * Delete a <code>VersionSpecification</code> specified by the given UUID.
     *
     * @param uuid The UUID.
     * @return <code>Result</code> status code indicating success or failure.
     */
    public Result deleteVersionSpecification(final UUID uuid) {
        final Optional<models.internal.VersionSpecification> versionSpecification =
                _versionSpecificationRepository.getVersionSpecification(uuid);

        if (!versionSpecification.isPresent()) {
            return notFound();
        }

        try {
            _versionSpecificationRepository.deleteVersionSpecification(uuid);
            // CHECKSTYLE.OFF: IllegalCatch - Convert any exception to 500
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            LOGGER.error()
                    .setMessage("Failed to deleteJob VersionSpecification")
                    .addData("Uuid", uuid)
                    .setThrowable(e)
                    .log();
            return internalServerError();
        }
        return ok();
    }

    private models.internal.VersionSet viewToInternalVersionSpec(final VersionSet view) {
        final DefaultVersionSet.Builder internalModel = new DefaultVersionSet.Builder();
        internalModel.setVersion(view.getVersion());
        final ArrayList<models.internal.PackageVersion> packageVersions = new ArrayList<>(view.getPackages().size());
        for (PackageVersion packageVersionView : view.getPackages().values()) {
            final DefaultPackageVersion packageVersion = new DefaultPackageVersion.Builder()
                    .setName(packageVersionView.getPackageName())
                    .setUri(packageVersionView.getUri())
                    .setVersion(packageVersionView.getVersion())
                    .build();
            packageVersions.add(packageVersion);
        }
        internalModel.setPackageVersions(packageVersions);
        return internalModel.build();
    }

    private models.internal.VersionSpecification viewToInternalVersionSpec(
            final VersionSpecification view,
            final models.internal.VersionSet versionSet) {
        final DefaultVersionSpecification.Builder internalModel = new DefaultVersionSpecification.Builder();
        internalModel.setUuid(view.getUuid());
        internalModel.setVersionSet(versionSet);
        internalModel.setPosition(view.getPosition());
        final ArrayList<VersionSpecificationAttribute> internalAttribs = new ArrayList<>(view.getTags().size());
        for (Map.Entry<String, String> tagKV : view.getTags().entrySet()) {
            final DefaultVersionSpecificationAttribute.Builder attrib = new DefaultVersionSpecificationAttribute.Builder();
            attrib.setKey(tagKV.getKey());
            attrib.setValue(tagKV.getValue());
            internalAttribs.add(attrib.build());
        }
        internalModel.setVersionSpecificationAttributes(internalAttribs);
        return internalModel.build();
    }


    private VersionSpecificationDetails internalToViewModel(final models.internal.VersionSpecification versionSpecification) {
        final VersionSpecificationDetails view = new VersionSpecificationDetails();
        view.setUuid(versionSpecification.getUuid());
        final Map<String, String> tags = Maps.newHashMapWithExpectedSize(versionSpecification.getVersionSpecificationAttributes().size());
        for (VersionSpecificationAttribute attribute : versionSpecification.getVersionSpecificationAttributes()) {
            tags.put(attribute.getKey(), attribute.getValue());
        }
        view.setTags(tags);
        view.setVersionSet(internalToViewModel(versionSpecification.getVersionSet()));
        return view;
    }

    private VersionSet internalToViewModel(final models.internal.VersionSet versionSet) {
        final VersionSet view = new VersionSet();
        view.setVersion(versionSet.getVersion());
        final Map<String, PackageVersion> packageViews = Maps.newHashMapWithExpectedSize(versionSet.getPackageVersions().size());
        for (models.internal.PackageVersion packageVersion : versionSet.getPackageVersions()) {
            final PackageVersion packageVersionView = new PackageVersion();
            packageVersionView.setPackageName(packageVersion.getName());
            packageVersionView.setVersion(packageVersion.getVersion());
            packageVersionView.setUri(packageVersion.getUri());
            packageViews.put(packageVersion.getName(), packageVersionView);
        }
        view.setPackages(packageViews);
        return view;
    }

    private Map<String, String> selectLastQueryParams(final Map<String, String[]> queryString) {
        return queryString.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
            final int lastIndex = entry.getValue().length - 1;
            return entry.getValue()[lastIndex];
        }));
    }

    private VersionSpecificationController(
            final int maxLimit,
            final VersionSpecificationRepository alertRepository) {
        _maxLimit = maxLimit;
        _versionSpecificationRepository = alertRepository;
    }

    private final int _maxLimit;
    private final VersionSpecificationRepository _versionSpecificationRepository;

    private static final int DEFAULT_MAX_LIMIT = 1000;
    private static final Logger LOGGER = LoggerFactory.getLogger(VersionSpecificationController.class);
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getInstance();
    private static final DateTimeFormatter RFC_1123_DATE_TIME = DateTimeFormatter.RFC_1123_DATE_TIME;
}
