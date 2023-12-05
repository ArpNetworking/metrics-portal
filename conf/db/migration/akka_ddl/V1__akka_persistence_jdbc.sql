/**
 * Copyright 2019 Dropbox
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
 
/**
 * Based on:
 *
 * https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/schema/postgres/postgres-schema.sql
 */

DROP TABLE IF EXISTS akka.event_journal;

DROP TABLE IF EXISTS akka.event_snapshot;

CREATE TABLE IF NOT EXISTS akka.event_journal(
                                                   ordering BIGSERIAL,
                                                   persistence_id VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    deleted BOOLEAN DEFAULT FALSE NOT NULL,

    writer VARCHAR(255) NOT NULL,
    write_timestamp BIGINT,
    adapter_manifest VARCHAR(255),

    event_ser_id INTEGER NOT NULL,
    event_ser_manifest VARCHAR(255) NOT NULL,
    event_payload BYTEA NOT NULL,

    meta_ser_id INTEGER,
    meta_ser_manifest VARCHAR(255),
    meta_payload BYTEA,

    PRIMARY KEY(persistence_id, sequence_number)
    );

CREATE UNIQUE INDEX event_journal_ordering_idx ON akka.event_journal(ordering);

CREATE TABLE IF NOT EXISTS akka.event_tag(
                                               event_id BIGINT,
                                               tag VARCHAR(256),
    PRIMARY KEY(event_id, tag),
    CONSTRAINT fk_event_journal
    FOREIGN KEY(event_id)
    REFERENCES event_journal(ordering)
    ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS akka.snapshot (
    persistence_id VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    created BIGINT NOT NULL,

    snapshot_ser_id INTEGER NOT NULL,
    snapshot_ser_manifest VARCHAR(255) NOT NULL,
    snapshot_payload BYTEA NOT NULL,

    meta_ser_id INTEGER,
    meta_ser_manifest VARCHAR(255),
    meta_payload BYTEA,

    PRIMARY KEY(persistence_id, sequence_number)
    );

CREATE TABLE IF NOT EXISTS akka.durable_state (
                                                    global_offset BIGSERIAL,
                                                    persistence_id VARCHAR(255) NOT NULL,
    revision BIGINT NOT NULL,
    state_payload BYTEA NOT NULL,
    state_serial_id INTEGER NOT NULL,
    state_serial_manifest VARCHAR(255),
    tag VARCHAR,
    state_timestamp BIGINT NOT NULL,
    PRIMARY KEY(persistence_id)
    );
CREATE INDEX state_tag_idx on akka.durable_state (tag);
CREATE INDEX state_global_offset_idx on akka.durable_state (global_offset);
