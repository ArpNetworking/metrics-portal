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

import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.pattern.PatternsCS;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.SnapshotOffer;
import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.mql.grammar.AlertTrigger;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.google.inject.Injector;
import models.internal.Alert;
import models.internal.NotificationEntry;
import models.internal.NotificationGroup;
import models.internal.Organization;
import org.joda.time.DateTime;
import scala.concurrent.duration.FiniteDuration;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Actor to dispatch notifications for an alert.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class NotificationActor extends AbstractPersistentActor {
    /**
     * Creates a props to build the actor.
     *
     * @param alertId the id of the alert causing the notification
     * @param organization organization the alert belongs to
     * @param alertRepository an alert repository
     * @param injector injector to create dependencies
     * @return a new {@link Props} to build the actor
     */
    public static Props props(
            final UUID alertId,
            final Organization organization,
            final AlertRepository alertRepository,
            final Injector injector) {
        return Props.create(NotificationActor.class, () -> new NotificationActor(alertId, organization, alertRepository, injector));
    }

    /**
     * Public constructor.
     *
     * @param alertId the id of the alert causing the notification
     * @param organization the organization owning the alert
     * @param alertRepository an alert repository
     * @param injector injector to create dependencies
     */
    public NotificationActor(
            final UUID alertId,
            final Organization organization,
            final AlertRepository alertRepository,
            final Injector injector) {
        _alertId = alertId;
        _organization = organization;
        _alertRepository = alertRepository;
        _injector = injector;
        context().setReceiveTimeout(FiniteDuration.apply(30, TimeUnit.MINUTES));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(AlertTrigger.class, trigger -> {
                    if (_lastAlertTime == null || trigger.getTime().isAfter(_lastAlertTime)) {
                        final Optional<Alert> alert = _alertRepository.get(_alertId, _organization);
                        final List<NotificationEntry> entries = alert.map(Alert::getNotificationGroup)
                                .map(NotificationGroup::getEntries)
                                .orElse(Collections.emptyList());
                        LOGGER.debug()
                                .setMessage("Dispatching trigger to entries")
                                .addData("entries", entries)
                                .addData("trigger", trigger)
                                .log();
                        final List<CompletionStage<Void>> futures = entries.stream()
                                .map(entry -> entry.notifyRecipient(alert.get(), trigger, _injector))
                                .collect(Collectors.toList());
                        final CompletableFuture<?>[] futureArray = futures.stream()
                                .map(CompletionStage::toCompletableFuture)
                                .toArray(CompletableFuture[]::new);
                        final CompletableFuture<NotificationsSent> all = CompletableFuture.allOf(futureArray)
                                .thenApply(v -> new NotificationsSent(trigger));
                        PatternsCS.pipe(all, context().dispatcher()).to(self());
                    }
                })
                .match(NotificationsSent.class, sent -> {
                    final AlertTrigger trigger = sent.getTrigger();
                    LOGGER.debug()
                            .setMessage("Trigger dispatched successfully")
                            .addData("trigger", trigger)
                            .log();
                    if (_lastAlertTime == null || trigger.getTime().isAfter(_lastAlertTime)) {
                        saveSnapshot(new NotificationState(trigger.getTime()));
                        if (trigger.getEndTime() != null) {
                            _lastAlertTime = trigger.getEndTime();
                        } else {
                            _lastAlertTime = trigger.getTime();
                        }
                    }
                })
                .match(ReceiveTimeout.class, timeout -> context().parent().tell(ShuttingDown.getInstance(), self()))
                .match(ShuttingDownAck.class, ack -> self().tell(PoisonPill.getInstance(), self()))
                .build();
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
                .match(SnapshotOffer.class, ss -> {
                    _lastAlertTime = ((NotificationState) ss.snapshot()).getLastNotificationTime();
                })
                .build();
    }

    @Override
    public String persistenceId() {
        return "alert-" + getSelf().path().name();
    }

    private DateTime _lastAlertTime = null;

    private final UUID _alertId;
    private final Organization _organization;
    private final AlertRepository _alertRepository;
    private final Injector _injector;

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationActor.class);

    private static final class NotificationsSent {
        NotificationsSent(final AlertTrigger trigger) {
            _trigger = trigger;
        }

        public AlertTrigger getTrigger() {
            return _trigger;
        }

        private final AlertTrigger _trigger;
    }

    private static final class NotificationState implements Serializable {
        NotificationState(final DateTime lastNotificationTime) {
            _lastNotificationTime = lastNotificationTime;
        }

        DateTime getLastNotificationTime() {
            return _lastNotificationTime;
        }

        private final DateTime _lastNotificationTime;

        private static final long serialVersionUID = 1L;
    }

    /**
     * Message used to indicate that the actor is shutting down.
     */
    protected static final class ShuttingDown {
        public static ShuttingDown getInstance() {
            return INSTANCE;
        }

        private ShuttingDown() { }

        private static final ShuttingDown INSTANCE = new ShuttingDown();
    }

    /**
     * Message used to acknowledge a shutdown notification.
     */
    protected static final class ShuttingDownAck {
        public static ShuttingDownAck getInstance() {
            return INSTANCE;
        }

        private ShuttingDownAck() { }

        private static final ShuttingDownAck INSTANCE = new ShuttingDownAck();
    }
}
