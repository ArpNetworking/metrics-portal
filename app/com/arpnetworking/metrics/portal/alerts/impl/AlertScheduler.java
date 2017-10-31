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

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.arpnetworking.metrics.portal.alerts.AlertRepository;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import models.internal.Alert;
import models.internal.AlertQuery;
import models.internal.Organization;
import models.internal.QueryResult;
import scala.concurrent.duration.Duration;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class AlertScheduler extends AbstractActorWithTimers {

    /**
     * Props factory.
     *
     * @param alertRepository An {@link AlertRepository}
     * @param alertExecutorRegion {@link ActorRef} for the alert executor shard region
     * @return a new props to create this actor
     */
    public static Props props(final AlertRepository alertRepository, final ActorRef alertExecutorRegion) {
        return Props.create(AlertScheduler.class, () -> new AlertScheduler(alertRepository, alertExecutorRegion));
    }

    /**
     * Public constructor.
     *
     * @param alertRepository An {@link AlertRepository}
     * @param alertExecutorRegion {@link ActorRef} for the alert executor shard region
     */
    @Inject
    public AlertScheduler(
            final AlertRepository alertRepository,
            @Named("alert-execution-shard-region")
            final ActorRef alertExecutorRegion) {
        _alertRepository = alertRepository;
        _alertExecutorRegion = alertExecutorRegion;
        timers().startPeriodicTimer("ALERT_REFRESH", RefreshAlerts.INSTANCE, Duration.apply(1, TimeUnit.MINUTES));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals(RefreshAlerts.INSTANCE, refresh -> {
                    LOGGER.info().setMessage("Refreshing alerts").log();

                    int totalOffset = 0;
                    QueryResult<Alert> result;
                    final Organization organization = Organization.DEFAULT;
                    do {
                        final AlertQuery query = _alertRepository.createQuery(organization)
                                .limit(1)
                                .offset(Optional.of(totalOffset));
                        result = _alertRepository.query(query);
                        final List<? extends Alert> values = result.values();
                        totalOffset += values.size();
                        LOGGER.debug().setMessage("Fetched alert page").addData("numAlerts", values.size()).log();
                        for (final Alert alert : values) {
                            _alertExecutorRegion.tell(new AlertExecutor.InstantiateAlert(alert.getId(), organization.getId()), self());
                        }
                    } while (result.total() > totalOffset);
                    LOGGER.info().setMessage("Completed alert refresh").addData("numAlerts", totalOffset).log();
                })
                .build();
    }

    private final AlertRepository _alertRepository;
    private final ActorRef _alertExecutorRegion;

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertScheduler.class);

    private static final class RefreshAlerts {
        public static final RefreshAlerts INSTANCE = new RefreshAlerts();

        private RefreshAlerts() { }
    }
}
