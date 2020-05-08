-- Copyright 2018 Dropbox, Inc.
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
CREATE TABLE portal.alert_executions (
    organization_id BIGINT NOT NULL,
    alert_id UUID NOT NULL,
    scheduled TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    state VARCHAR(255),
    result TEXT,
    error TEXT

--     What version of postgres do we run? This is supported in v11 but the local env runs v10
--     PRIMARY KEY (organization_id, alert_id, scheduled)
) PARTITION BY RANGE (scheduled);

-- CREATE INDEX alert_executions_state_completed_at_idx ON portal.alert_executions (alert_id, completed_at desc, state);

CREATE TABLE portal.alert_executions_y2020m05d07 PARTITION OF portal.alert_executions
    FOR VALUES FROM ('2020-05-07') TO ('2020-05-08');
CREATE TABLE portal.alert_executions_y2020m05d08 PARTITION OF portal.alert_executions
    FOR VALUES FROM ('2020-05-08') TO ('2020-05-09');
CREATE TABLE portal.alert_executions_y2020m05d09 PARTITION OF portal.alert_executions
    FOR VALUES FROM ('2020-05-09') TO ('2020-05-10');
CREATE TABLE portal.alert_executions_y2020m05d10 PARTITION OF portal.alert_executions
    FOR VALUES FROM ('2020-05-10') TO ('2020-05-11');
CREATE TABLE portal.alert_executions_y2020m05d11 PARTITION OF portal.alert_executions
    FOR VALUES FROM ('2020-05-11') TO ('2020-05-12');
