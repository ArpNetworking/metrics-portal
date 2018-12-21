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
    timeout_seconds INTEGER NOT NULL,
    type VARCHAR(255) NOT NULL,

    -- CHROME_SCREENSHOT
    url VARCHAR(255),
    title VARCHAR(255) DEFAULT '',
    ignore_certificate_errors BIT DEFAULT 0,
    triggering_event_name VARCHAR(255),
);

CREATE UNIQUE INDEX report_sources_uuid_idx ON portal.report_sources (uuid);

CREATE TABLE portal.report_schedules (
    id BIGSERIAL PRIMARY KEY,
    start_date DATE NOT NULL,
    available_at TIMESTAMP NOT NULL DEFAULT '00:00:00',
    type VARCHAR(255) NOT NULL,

--  Recurring Events
    end_date DATE DEFAULT NULL,
    max_occurrences INTEGER DEFAULT NULL,
--  WEEKLY
--     days_of_week INTEGER,
--  MONTHLY_FIXED_DATE
--     day_of_month INTEGER,
--  MONTHLY_FIXED_DAY_OF_WEEK
--     kth_day INTEGER,
--     day_of_week INTEGER,
);

CREATE TABLE portal.reporting_jobs (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    name VARCHAR(255) NOT NULL,
    disabled bit DEFAULT 0,

    report_source_id BIGINT NOT NULL references portal.report_sources(id),
    report_schedule_id BIGINT NOT NULL references portal.report_schedules(id),
);

CREATE TABLE portal.report_recipient_groups (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    name VARCHAR(255) DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    reporting_job_id BIGINT NOT NULL references portal.reporting_jobs(id),
);

CREATE TABLE portal.report_recipients (
    id BIGSERIAL PRIMARY KEY,
    recipient_group_id BIGINT NOT NULL references portal.report_recipient_groups,
    recipient VARCHAR NOT NULL,
    type VARCHAR(255) NOT NULL,
);

CREATE UNIQUE INDEX report_recipient_groups_uuid_idx ON portal.report_recipient_groups (uuid);
CREATE UNIQUE INDEX report_recipient_groups_name_idx ON portal.report_recipient_groups (name);

CREATE TABLE portal.report_formats (
    id BIGSERIAL PRIMARY KEY,
    recipient_group_id BIGINT NOT NULL references portal.report_recipient_groups(id),
    type VARCHAR(255) NOT NULL,
    -- PDF
    width_inches FLOAT,
    height_inches FLOAT,
);

CREATE UNIQUE INDEX reporting_jobs_uuid_idx ON portal.reporting_jobs (uuid);
CREATE UNIQUE INDEX reporting_jobs_name_idx ON portal.reporting_jobs (name);
CREATE INDEX reporting_jobs_disabled_idx ON portal.reporting_jobs (disabled);
