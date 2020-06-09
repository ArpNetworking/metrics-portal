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
CREATE OR REPLACE FUNCTION create_daily_partition(schema TEXT, tablename TEXT, start_date DATE, end_date DATE)
returns void AS $$
DECLARE
    partition_table text;
    day DATE;
BEGIN
    FOR day IN (SELECT d FROM generate_series(start_date, end_date, '1 day') AS d)
    LOOP
        partition_table := tablename || '_' || TO_CHAR(day, 'YYYY_MM_DD');
        EXECUTE format(
            $query$
                CREATE TABLE IF NOT EXISTS %I.%I PARTITION OF %I.%I FOR VALUES FROM (%L) TO (%L);
            $query$,
            schema,
            partition_table,
            schema,
            tablename,
            day,
            day + 1
        );
    END LOOP;
END;
$$
language plpgsql;
