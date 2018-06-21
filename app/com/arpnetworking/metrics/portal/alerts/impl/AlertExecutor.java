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
package com.arpnetworking.metrics.portal.alerts.impl;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Status;
import akka.actor.Terminated;
import akka.cluster.sharding.ShardRegion;
import akka.pattern.PatternsCS;
import akka.persistence.AbstractPersistentActorWithTimers;
import com.arpnetworking.kairos.client.models.MetricsQueryResponse;
import com.arpnetworking.metrics.incubator.PeriodicMetrics;
import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.metrics.portal.query.QueryExecutor;
import com.arpnetworking.mql.grammar.AlertTrigger;
import com.arpnetworking.mql.grammar.TimeSeriesResult;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.inject.Injector;
import models.internal.Alert;
import models.internal.Organization;
import models.internal.impl.DefaultOrganization;
import org.joda.time.Duration;
import org.joda.time.Period;
import scala.concurrent.duration.FiniteDuration;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * Actor responsible for executing and evaluating an alert query.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class AlertExecutor extends AbstractPersistentActorWithTimers {


    /**
     * Public constructor.
     *
     * @param alertRepository an alert repository
     * @param queryExecutor a query executor
     * @param injector injector to create dependencies
     * @param periodicMetrics periodic metrics instance to record against
     */
    @Inject
    public AlertExecutor(
            final AlertRepository alertRepository,
            final QueryExecutor queryExecutor,
            final Injector injector,
            final PeriodicMetrics periodicMetrics) {
        _alertRepository = alertRepository;
        _queryExecutor = queryExecutor;
        _injector = injector;
        _periodicMetrics = periodicMetrics;
        LOGGER.debug()
                .setMessage("Starting alert executor")
                .addData("name", self().path().name())
                .log();
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
                .match(InstantiateAlert.class, message -> {
                    _alertId = message.getAlertId();
                    _organization = new DefaultOrganization.Builder().setId(message.getOrganizationId()).build();
                    LOGGER.debug()
                            .setMessage("Instantiating alert from recovery")
                            .addData("alertId", _alertId)
                            .log();
                    loadAlert();
                })
                .build();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(InstantiateAlert.class, message -> {
                    // We have already persisted the ID and recovered it
                    if (_alertId != null) {
                        return;
                    }
                    persist(message, msg -> {
                        _alertId = message.getAlertId();
                        _organization = new DefaultOrganization.Builder().setId(message.getOrganizationId()).build();
                        LOGGER.debug()
                                .setMessage("Instantiating alert")
                                .addData("alertId", _alertId)
                                .log();
                        loadAlert();
                    });
                })
                .match(RefreshAlert.class, refresh -> {
                    loadAlert();
                })
                .match(ExecuteAlert.class, executeAlert -> {
                    LOGGER.debug()
                            .setMessage("Executing alert")
                            .addData("alertId", _alertId)
                            .log();
                    _periodicMetrics.recordCounter(ALERT_EXECUTED_METRIC, 1);
                    PatternsCS.pipe(_queryExecutor.executeQuery(_query), context().dispatcher()).to(self());
                })
                .match(SendTo.class, sendTo -> {
                    if (_alertId == null) {
                        self().tell(new InstantiateAlert(sendTo.getAlertId(), sendTo.getOrganizationId()), self());
                    }
                    self().tell(sendTo.getPayload(), getSender());
                })
                .match(Status.Failure.class, failure -> {
                    _periodicMetrics.recordCounter(ALERT_EXECUTED_FAILURE_METRIC, 1);
                    LOGGER.warn()
                            .setMessage("failed to execute alert")
                            .addData("alertId", _alertId)
                            .setThrowable(failure.cause())
                            .log();
                    //TODO(brandon): send email about alert failure
                })
                .match(TimeSeriesResult.class, this::processTimeSeriesResult)
                .match(NotificationActor.ShuttingDown.class, // Schedule a cleanup task to make sure the shutdown happens properly
                        this::notifiationActorShuttingDown)
                .match(Terminated.class, // Check to see if there are any pending messages that would require re-launching
                        this::notificationActorTerminated)
                .match(FlushNotificationActor.class, // Check to see if there are any pending messages that would require re-launching
                        this::flushNotificationActor)
                .build();
    }

    private void processTimeSeriesResult(final TimeSeriesResult series) {
        if (series.getErrors().size() > 0) {
            LOGGER.warn()
                    .setMessage("Errors when executing alert query")
                    .addData("alertId", _alertId)
                    .addData("errors", series.getErrors())
                    .log();
        }

        LOGGER.debug()
                .setMessage("Alert execution completed")
                .addData("alertId", _alertId)
                .log();

        series.getResponse().getQueries().stream()
                .map(MetricsQueryResponse.Query::getResults)
                .flatMap(ImmutableList::stream)
                .map(MetricsQueryResponse.QueryResult::getAlerts)
                .flatMap(ImmutableList::stream)
                .filter(alert ->  alert.getTime().plus(Duration.standardSeconds(120)).isBeforeNow())
                .forEach(alert -> {
                    final String notificationId = hashAlert(alert);
                    final ActorRef notificationActor = getOrLaunchActor(notificationId);
                    // If it's shutting down, we enqueue to the pending messages for that actor
                    if (_shuttingDownNotifications.containsKey(notificationActor)) {
                        _pendingMessages.put(notificationId, alert);

                        // Timeout the shutdown and launch it again to make sure it's up and running
                        final ZonedDateTime shuttingDownTime = _shuttingDownNotifications.get(notificationActor);
                        if (shuttingDownTime.isBefore(ZonedDateTime.now().minus(3, ChronoUnit.MINUTES))) {
                            _shuttingDownNotifications.remove(notificationActor);
                        }
                    } else {
                        notificationActor.tell(alert, self());
                    }
                });
    }

    private ActorRef getOrLaunchActor(final String notificationId) {
        return _notificationActors.computeIfAbsent(
                notificationId,
                hash -> {
                    final ActorRef actor = context().actorOf(
                            NotificationActor.props(_alertId, _organization, _alertRepository, _periodicMetrics, _injector),
                            notificationId);
                    context().watch(actor);
                    _pendingMessages.get(notificationId).forEach(msg -> actor.tell(msg, self()));
                    _pendingMessages.removeAll(notificationId);
                    return actor;
                });
    }

    private String hashAlert(final AlertTrigger alert) {
        return alert.getArgs()
                .entrySet()
                .stream()
                .map(entry -> String.format("%s:%s", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(";"))
                .replace('/', '.');
    }

    private void loadAlert() {
        final Optional<Alert> alertOptional = _alertRepository.get(_alertId, _organization);
        if (alertOptional.isPresent()) {
            final Alert alert = alertOptional.get();
            if (!timers().isTimerActive("ALERT_REFRESH")) {
                timers().startPeriodicTimer("ALERT_REFRESH", RefreshAlert.getInstance(), FiniteDuration.apply(5, TimeUnit.MINUTES));
            }
            if (!alert.getPeriod().equals(_executePeriod)) {
                _executePeriod = alert.getPeriod();
                timers().startPeriodicTimer(
                        "ALERT_EXECUTE",
                        ExecuteAlert.getInstance(),
                        FiniteDuration.apply(_executePeriod.toStandardDuration().getMillis(), TimeUnit.MILLISECONDS));
            }
            if (!alert.getQuery().equals(_query)) {
                _query = alert.getQuery();
            }
            _periodicMetrics.recordCounter(ALERT_LOAD_METRIC, 1);
        } else {
            LOGGER.info()
                    .setMessage("Tried loading executor for alert that does not exist, shutting down executor")
                    .addData("alertId", _alertId)
                    .log();
            context().parent().tell(new ShardRegion.Passivate(PoisonPill.getInstance()), self());
            _periodicMetrics.recordCounter(ALERT_LOAD_FAILURE_METRIC, 1);
        }
    }

    private void flushNotificationActor(final FlushNotificationActor flush) {
        final ActorRef ref = flush.getRef();
        if (_shuttingDownNotifications.containsKey(ref)) {
            LOGGER.warn()
                    .setMessage("Flushing actor that did not shutdown properly")
                    .addData("notificationActor", ref)
                    .log();
            context().stop(ref);
            _shuttingDownNotifications.remove(ref);
            // Check to see if there are any pending messages that would require re-launching
            final String notificationId = _notificationActors.inverse().get(ref);
            _notificationActors.remove(notificationId);
            if (!_pendingMessages.get(notificationId).isEmpty()) {
                getOrLaunchActor(notificationId);
            }
        }
    }

    private void notificationActorTerminated(final Terminated terminated) {
        final ActorRef dead = terminated.actor();
        if (!_shuttingDownNotifications.containsKey(dead)) {
            LOGGER.warn()
                    .setMessage("Notification actor died without sending shutdown")
                    .addData("notificationActor", dead)
                    .log();
        }
        _shuttingDownNotifications.remove(dead);
        // Check to see if there are any pending messages that would require re-launching
        final String notificationId = _notificationActors.inverse().get(dead);
        _notificationActors.remove(notificationId);
        if (!_pendingMessages.get(notificationId).isEmpty()) {
            getOrLaunchActor(notificationId);
        }
    }

    private void notifiationActorShuttingDown(final NotificationActor.ShuttingDown shuttingDown) {
        _shuttingDownNotifications.put(context().sender(), ZonedDateTime.now());

        // Schedule a cleanup task to make sure the shutdown happens properly
        context().system().scheduler().scheduleOnce(
                FiniteDuration.apply(2, TimeUnit.MINUTES),
                self(),
                new FlushNotificationActor(context().sender()),
                context().system().dispatcher(),
                self());

        sender().tell(NotificationActor.ShuttingDownAck.getInstance(), self());
    }

    @Override
    public String persistenceId() {
        return "alert-" + getSelf().path().name();
    }

    private UUID _alertId;
    private Organization _organization;
    private Period _executePeriod;
    private String _query;

    private final QueryExecutor _queryExecutor;
    private final Injector _injector;
    private final PeriodicMetrics _periodicMetrics;
    private final AlertRepository _alertRepository;
    private final BiMap<String, ActorRef> _notificationActors = HashBiMap.create();
    private final Map<ActorRef, ZonedDateTime> _shuttingDownNotifications = Maps.newHashMap();
    private final Multimap<String, Object> _pendingMessages = HashMultimap.create();

    private static final String METRICS_PREFIX = "alert/executor/";
    private static final String ALERT_LOAD_METRIC = METRICS_PREFIX + "alert_load";
    private static final String ALERT_LOAD_FAILURE_METRIC = METRICS_PREFIX + "alert_load_failure";
    private static final String ALERT_EXECUTED_METRIC = METRICS_PREFIX + "execute";
    private static final String ALERT_EXECUTED_FAILURE_METRIC = METRICS_PREFIX + "execute_failure";
    private static final Logger LOGGER = LoggerFactory.getLogger(AlertExecutor.class);

    /**
     * Message class to instantiate an alert.
     */
    public static final class InstantiateAlert {
        /**
         * Public constructor.
         *
         * @param alertId the id of the alert
         * @param organizationId the id of the organization
         */
        public InstantiateAlert(final UUID alertId, final UUID organizationId) {
            _alertId = alertId;
            _organizationId = organizationId;
        }

        public UUID getAlertId() {
            return _alertId;
        }

        public UUID getOrganizationId() {
            return _organizationId;
        }

        private final UUID _alertId;
        private final UUID _organizationId;
    }

    /**
     * Message class to execute the alert check.
     */
    public static final class ExecuteAlert {
        /**
         * Get the singleton instance.
         *
         * @return the singleton instance
         */
        public static ExecuteAlert getInstance() {
            return INSTANCE;
        }

        private ExecuteAlert() { }

        private static final ExecuteAlert INSTANCE = new ExecuteAlert();
    }

    /**
     * Message class to initiate a refresh of the alert.
     */
    public static final class RefreshAlert {
        /**
         * Get the singleton instance.
         *
         * @return the singleton instance
         */
        public static RefreshAlert getInstance() {
            return INSTANCE;
        }

        private RefreshAlert() { }

        private static final RefreshAlert INSTANCE = new RefreshAlert();
    }

    /**
     * Message envelope to facilitate sending messages without ids to sharded alert actors.
     */
    public static final class SendTo {
        /**
         * Public constructor.
         *
         * @param alertId the alert id for the actor to send to
         * @param organizationId the id of the organization
         * @param payload wrapped payload
         */
        public SendTo(final UUID alertId, final UUID organizationId, final Object payload) {
            _alertId = alertId;
            _organizationId = organizationId;
            _payload = payload;
        }

        public UUID getAlertId() {
            return _alertId;
        }

        public UUID getOrganizationId() {
            return _organizationId;
        }

        public Object getPayload() {
            return _payload;
        }

        private final UUID _alertId;
        private final UUID _organizationId;
        private final Object _payload;
    }

    private static class FlushNotificationActor {
        FlushNotificationActor(final ActorRef ref) {

            _ref = ref;
        }

        public ActorRef getRef() {
            return _ref;
        }

        private final ActorRef _ref;

    }
}
