-- Copyright 2020 Dropbox, Inc.
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

-- This table does not have a primary key constraint as that is not supported in Postgres 10 for partitioned tables.
CREATE TABLE portal.alert_executions (
    organization_id BIGINT NOT NULL,
    alert_id UUID NOT NULL,
    scheduled TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    state VARCHAR(255),
    result TEXT,
    error TEXT
) PARTITION BY RANGE (scheduled);

-- Create daily partition tables <TABLE>_YEAR_MONTH_DAY for a specified parent table.
--
--     Example for portal.alert_executions, 8 May 2020:
--
--     CREATE TABLE portal.alert_executions_2020_05_08 PARTITION OF portal.alert_executions
--            FOR VALUES FROM ('2020-05-08') TO ('2020-05-09');
--
-- Params:
--    Table - Text - The name of the parent table.
--    Start - Date - The beginning date of the time range, inclusive.
--    End   - Date - The end date of the time range, exclusive.
CREATE OR REPLACE FUNCTION create_daily_partition( TEXT, DATE, DATE )
returns INTEGER AS $$
DECLARE
    create_query text;
    created INTEGER := 0;
    tables_created INTEGER := 0;
BEGIN
    FOR create_query IN (SELECT
        'CREATE TABLE IF NOT EXISTS ' || $1 || '_' || TO_CHAR(d, 'YYYY_MM_DD') ||
        ' PARTITION OF ' || $1 || E' FOR VALUES FROM (\'' || d::date || E'\') TO (\'' || d::date + 1 || E'\');'
        FROM generate_series($2, $3, '1 day') as d)
    LOOP
        EXECUTE create_query INTO created;
        IF (created > 0) THEN
            tables_created := tables_created + 1;
        END IF;
    END LOOP;
    RETURN tables_created;
END;
$$
language plpgsql;

-- Create an initial partition.
SELECT create_daily_partition('portal.alert_executions', CURRENT_DATE, CURRENT_DATE + 1);
