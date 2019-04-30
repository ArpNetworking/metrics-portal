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
package models.internal;

import java.time.Duration;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Internal model interface for an alert.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot io)
 */
public interface Alert {

    /**
     * The unique identifier of the alert.
     *
     * @return The unique identifier of the alert.
     */
    UUID getId();

    /**
     * The context of the alert. Either a host or cluster.
     *
     * @return The context of the alert.
     */
    Context getContext();

    /**
     * The name of the alert.
     *
     * @return The name of the alert.
     */
    String getName();

    /**
     * The name of the cluster for statistic identifier of condition left-hand side.
     *
     * @return The name of the cluster for statistic identifier of condition left-hand side.
     */
    String getCluster();

    /**
     * The name of the service for statistic identifier of condition left-hand side.
     *
     * @return The name of the service for statistic identifier of condition left-hand side.
     */
    String getService();

    /**
     * The name of the metric for statistic identifier of condition left-hand side.
     *
     * @return The name of the metric for statistic identifier of condition left-hand side.
     */
    String getMetric();

    /**
     * The name of the statistic for statistic identifier of condition left-hand side.
     *
     * @return The name of the statistic for statistic identifier of condition left-hand side.
     */
    String getStatistic();

    /**
     * The period to evaluate the condition in.
     *
     * @return The period to evaluate the condition in.
     */
    Duration getPeriod();

    /**
     * The condition operator.
     *
     * @return The condition operator.
     */
    Operator getOperator();

    /**
     * The value of condition right-hand side.
     *
     * @return The value of condition right-hand side.
     */
    Quantity getValue();

    /**
     * Nagios specific extensions.
     *
     * @return Nagios specific extensions.
     */
    @Nullable
    NagiosExtension getNagiosExtension();
}
