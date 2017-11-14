/**
 * Copyright 2017 Smartsheet
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

import com.arpnetworking.metrics.portal.notifications.NotificationRepository;
import com.arpnetworking.metrics.portal.organizations.OrganizationProvider;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Maps;
import com.google.common.net.HttpHeaders;
import com.typesafe.config.Config;
import models.internal.NotificationEntry;
import models.internal.NotificationGroup;
import models.internal.NotificationGroupQuery;
import models.internal.Organization;
import models.internal.QueryResult;
import models.view.Notification;
import models.view.PagedContainer;
import models.view.Pagination;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Controller to send manage notification groups and send notifications.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
@Singleton
public class NotificationController extends Controller {
    /**
     * Public constructor.
     *
     * @param mapper Instance of an {@link ObjectMapper}
     * @param configuration Application configuration
     * @param notificationRepository Instance of a {@link NotificationRepository}
     * @param organizationProvider An {@link OrganizationProvider} to get the organization from the request
     */
    @Inject
    public NotificationController(
            final ObjectMapper mapper,
            final Config configuration,
            final NotificationRepository notificationRepository,
            final OrganizationProvider organizationProvider) {
        _mapper = mapper;
        _notificationRepository = notificationRepository;
        _maxLimit = configuration.getInt("notifications.limit");
        _organizationProvider = organizationProvider;
    }

    /**
     * Adds or updates a {@link NotificationGroup}.
     *
     * @return Ok if the notification group was created or updated successfully, a failure HTTP status code otherwise.
     */
    public Result addOrUpdate() {
        final NotificationGroup notificationGroup;
        try {
            notificationGroup = _mapper.treeToValue(request().body().asJson(), models.view.NotificationGroup.class).toInternal();
        } catch (final IOException e) {
            LOGGER.error()
                    .setMessage("Failed to build a notification group.")
                    .setThrowable(e)
                    .log();
            return badRequest("Invalid request body.");
        }

        try {
            _notificationRepository.addOrUpdateNotificationGroup(notificationGroup, _organizationProvider.getOrganization(request()));
            // CHECKSTYLE.OFF: IllegalCatch - Convert any exception to 500
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            LOGGER.error()
                    .setMessage("Failed to add an notificationGroup.")
                    .setThrowable(e)
                    .log();
            return internalServerError();
        }
        return noContent();
    }

    /**
     * Adds a recipient to a {@link NotificationGroup}.
     *
     * @param id Id of the notification group
     * @return NoContent if the notification group was created or updated successfully, a failure HTTP status code otherwise.
     */
    public Result addRecipient(final String id) {
        final UUID identifier;
        try {
            identifier = UUID.fromString(id);
        } catch (final IllegalArgumentException e) {
            return badRequest();
        }
        final NotificationEntry notificationEntry;
        try {
            notificationEntry = _mapper.treeToValue(request().body().asJson(), models.view.NotificationEntry.class).toInternal();
        } catch (final IOException e) {
            LOGGER.error()
                    .setMessage("Failed to build a notification recipient.")
                    .setThrowable(e)
                    .log();
            return badRequest("Invalid request body.");
        }

        try {
            final Organization organization = _organizationProvider.getOrganization(request());
            final Optional<NotificationGroup> group = _notificationRepository.getNotificationGroup(identifier, organization);
            if (!group.isPresent()) {
                return notFound();
            } else {
                final NotificationGroup notificationGroup = group.get();
                if (!notificationGroup.getEntries().contains(notificationEntry)) {
                    _notificationRepository.addRecipientToNotificationGroup(notificationGroup, organization, notificationEntry);
                } else {
                    // We already have the recipient
                    LOGGER.debug()
                            .setMessage("Recipient already in notification group")
                            .addData("group", group)
                            .addData("recipient", notificationEntry)
                            .addData("organization", organization)
                            .log();
                }
            }
            // CHECKSTYLE.OFF: IllegalCatch - Convert any exception to 500
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            LOGGER.error()
                    .setMessage("Failed to add recipient to notification group.")
                    .setThrowable(e)
                    .log();
            return internalServerError();
        }
        return noContent();
    }

    /**
     * Removes a recipient from a {@link NotificationGroup}.
     *
     * @param id Id of the notification group
     * @return NoContent if the notification group was removed successfully (or if the recipient was not on the list),
     * a failure HTTP status code otherwise.
     */
    public Result removeRecipient(final String id) {
        final UUID identifier;
        try {
            identifier = UUID.fromString(id);
        } catch (final IllegalArgumentException e) {
            return badRequest();
        }
        final NotificationEntry notificationEntry;
        try {
            final JsonNode json = request().body().asJson();
            final models.view.NotificationEntry viewEntry = _mapper.treeToValue(json, models.view.NotificationEntry.class);
            notificationEntry = viewEntry.toInternal();
        } catch (final IOException e) {
            LOGGER.error()
                    .setMessage("Failed to build a notification recipient.")
                    .setThrowable(e)
                    .log();
            return badRequest("Invalid request body.");
        }

        try {
            final Organization organization = _organizationProvider.getOrganization(request());
            final Optional<NotificationGroup> group = _notificationRepository.getNotificationGroup(identifier, organization);
            if (!group.isPresent()) {
                return notFound();
            } else {
                final NotificationGroup notificationGroup = group.get();
                notificationGroup.getEntries().remove(notificationEntry);
                _notificationRepository.removeRecipientFromNotificationGroup(notificationGroup, organization, notificationEntry);
            }
            // CHECKSTYLE.OFF: IllegalCatch - Convert any exception to 500
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            LOGGER.error()
                    .setMessage("Failed to add recipient to notification group.")
                    .setThrowable(e)
                    .log();
            return internalServerError();
        }
        return noContent();
    }

    /**
     * Query for notification groups.
     *
     * @param contains The text to search for. Optional.
     * @param limit The maximum number of results to return. Optional.
     * @param offset The number of results to skip. Optional.
     * @return <code>Result</code> paginated matching notification groups.
     */
    // CHECKSTYLE.OFF: ParameterNameCheck - Names must match query parameters.
    public Result query(
            final String contains,
            final Integer limit,
            final Integer offset) {
        // CHECKSTYLE.ON: ParameterNameCheck

        // Convert and validate parameters
        final Optional<String> argContains = Optional.ofNullable(contains);
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

        // Build a host repository query
        final NotificationGroupQuery query = _notificationRepository.createQuery(_organizationProvider.getOrganization(request()))
                .contains(argContains)
                .limit(argLimit)
                .offset(argOffset);

        // Execute the query
        final QueryResult<NotificationGroup> result;
        try {
            result = query.execute();
            // CHECKSTYLE.OFF: IllegalCatch - Convert any exception to 500
        } catch (final Exception e) {
            // CHECKSTYLE.ON: IllegalCatch
            LOGGER.error()
                    .setMessage("Notification group query failed")
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
                        .map(NotificationGroup::toView)
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
     * Get specific notification group.
     *
     * @param id The identifier of the notification group.
     * @return Matching notification group.
     */
    public Result get(final String id) {
        final UUID identifier;
        try {
            identifier = UUID.fromString(id);
        } catch (final IllegalArgumentException e) {
            return badRequest();
        }
        final Organization organization = _organizationProvider.getOrganization(request());
        final Optional<NotificationGroup> result = _notificationRepository.getNotificationGroup(identifier, organization);
        if (!result.isPresent()) {
            return notFound();
        }
        // Return as JSON
        return ok(Json.toJson(result.get().toView()));
    }

    /**
     * Notifies recipients of an event.
     *
     * @return <code>Result</code> of NoContent or error
     */
    public CompletionStage<Result> notifyEvent() {
        final JsonNode body = request().body().asJson();
        if (body == null) {
            throw new RuntimeException("null body for notification");
        }

        final Notification notification;
        try {
            notification = _mapper.treeToValue(body, Notification.class);
        } catch (final IOException e) {
            throw new RuntimeException("invalid body for notification");
        }

        //TODO(brandon): get the recipients for this notification
        final Optional<NotificationGroup> notificationGroup =
                _notificationRepository.getNotificationGroup(notification.getAlertId(), _organizationProvider.getOrganization(request()));
        if (!notificationGroup.isPresent()) {
            LOGGER.warn()
                    .setMessage("No notification group found for notification")
                    .addData("alertId", notification.getAlertId())
                    .log();
            throw new IllegalStateException("Alert should have a notification group. AlertId: " + notification.getAlertId());
        }

        return CompletableFuture.completedFuture(Results.noContent());
    }

    private final int _maxLimit;
    private final OrganizationProvider _organizationProvider;
    private final ObjectMapper _mapper;
    private final NotificationRepository _notificationRepository;
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationController.class);
}
