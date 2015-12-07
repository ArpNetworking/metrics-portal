/**
 * Copyright 2015 Groupon
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
CREATE TABLE portal.alerts (
    id SERIAL PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    uuid UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    cluster VARCHAR(255) NOT NULL,
    service VARCHAR(255) NOT NULL,
    context VARCHAR(20) NOT NULL CHECK (context IN ('HOST', 'CLUSTER')),
    metric VARCHAR(255) NOT NULL,
    statistic VARCHAR(255) NOT NULL,
    period_in_seconds INTEGER NOT NULL,
    operator VARCHAR(20) NOT NULL CHECK (operator IN ('EQUAL_TO', 'NOT_EQUAL_TO', 'LESS_THAN', 'LESS_THAN_OR_EQUAL_TO', 'GREATER_THAN', 'GREATER_THAN_OR_EQUAL_TO')),
    quantity_value NUMERIC NOT NULL,
    quantity_unit VARCHAR(20)
);

CREATE UNIQUE INDEX alerts_uuid_idx ON portal.alerts (uuid);

CREATE TABLE portal.nagios_extensions (
    id SERIAL PRIMARY KEY,
    alert_id INTEGER REFERENCES portal.alerts,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    severity VARCHAR(20) NOT NULL,
    notify VARCHAR(255) NOT NULL,
    max_check_attempts INTEGER NOT NULL,
    freshness_threshold_in_seconds INTEGER NOT NULL
);

CREATE TABLE portal.expressions (
    id SERIAL PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    uuid UUID NOT NULL,
    cluster VARCHAR(255) NOT NULL,
    service VARCHAR(255) NOT NULL,
    metric VARCHAR(255) NOT NULL,
    script TEXT NOT NULL
);

CREATE UNIQUE INDEX expressions_uuid_idx ON portal.expressions (uuid);
