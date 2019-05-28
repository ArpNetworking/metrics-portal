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
CREATE TABLE portal.report_sources (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    type VARCHAR(255) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- WEB_PAGE
    url VARCHAR(255),
    title VARCHAR(255) DEFAULT '',
    ignore_certificate_errors BOOLEAN NOT NULL DEFAULT FALSE,
    triggering_event_name VARCHAR(255)
);

CREATE UNIQUE INDEX report_sources_uuid_idx ON portal.report_sources (uuid);

CREATE TABLE portal.report_schedules (
    id BIGSERIAL PRIMARY KEY,
    run_at TIMESTAMP NOT NULL,
    run_until TIMESTAMP DEFAULT NULL,
    type VARCHAR(255) NOT NULL,

--  Recurring Events
    offset_duration BIGINT DEFAULT 0,
    period VARCHAR(255),
    zone VARCHAR(255)
);

CREATE TABLE portal.reports (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL,
    organization_id BIGINT NOT NULL references portal.organizations(id),
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    name VARCHAR(255) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    report_source_id BIGINT NOT NULL references portal.report_sources(id),
    report_schedule_id BIGINT NOT NULL references portal.report_schedules(id)
);

CREATE UNIQUE INDEX reports_uuid_idx ON portal.reports (uuid);

CREATE TABLE portal.report_executions (
    report_id BIGINT NOT NULL references portal.reports(id),
    scheduled TIMESTAMP NOT NULL DEFAULT now(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    state VARCHAR(255),
    result TEXT,
    error TEXT,

    PRIMARY KEY (report_id, scheduled)
);

CREATE INDEX report_executions_state_completed_at_idx ON portal.report_executions (report_id, completed_at desc, state);

CREATE TABLE portal.recipients (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    uuid UUID NOT NULL,
    address VARCHAR NOT NULL,
    type VARCHAR(255) NOT NULL
);

CREATE UNIQUE INDEX recipients_uuid_idx ON portal.recipients (uuid);

CREATE TABLE portal.report_formats (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    type VARCHAR(255) NOT NULL,
    -- PDF
    width_inches FLOAT,
    height_inches FLOAT
);

CREATE TABLE portal.reports_to_recipients (
  report_id BIGINT NOT NULL references portal.reports(id),
  recipient_id BIGINT NOT NULL references portal.recipients(id),
  format_id BIGINT NOT NULL references portal.report_formats(id),
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  PRIMARY KEY (report_id, recipient_id)
);
