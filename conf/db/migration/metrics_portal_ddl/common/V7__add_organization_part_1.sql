/**
 * Copyright 2016 Smartsheet.com
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

/* Update all version columns to be bigint */
ALTER TABLE portal.hosts ALTER COLUMN version TYPE BIGINT;
ALTER TABLE portal.alerts ALTER COLUMN version TYPE BIGINT;
ALTER TABLE portal.nagios_extensions ALTER COLUMN version TYPE BIGINT;
ALTER TABLE portal.expressions ALTER COLUMN version TYPE BIGINT;
ALTER TABLE portal.hosts ALTER COLUMN version TYPE BIGINT;

/* Update all foreign key id columns to be bigint */
ALTER TABLE portal.nagios_extensions ALTER COLUMN alert_id TYPE BIGINT;
ALTER TABLE portal.version_set_package_versions ALTER COLUMN version_set_id TYPE BIGINT;
ALTER TABLE portal.version_set_package_versions ALTER COLUMN package_version_id TYPE BIGINT;
ALTER TABLE portal.version_specification_attributes ALTER COLUMN version_specification TYPE BIGINT;
ALTER TABLE portal.version_specifications ALTER COLUMN version_set_id TYPE BIGINT;
ALTER TABLE portal.version_specifications ALTER COLUMN next TYPE BIGINT;

CREATE TABLE portal.organizations (
  id BIGSERIAL PRIMARY KEY,
  uuid UUID NOT NULL,
  version BIGINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX organizations_uuid_idx ON portal.organizations (uuid);

INSERT INTO portal.organizations (uuid) VALUES ('0eb03110-2a36-4cb1-861f-7375afc98b9b');

ALTER TABLE portal.hosts ADD COLUMN organization BIGINT DEFAULT NULL;
UPDATE portal.hosts SET organization = (SELECT MIN(id) FROM portal.organizations);
ALTER TABLE portal.hosts ADD FOREIGN KEY (organization) REFERENCES organizations(id);
ALTER TABLE portal.hosts ALTER COLUMN organization SET NOT NULL;

DROP INDEX portal.hosts_name_idx;
DROP INDEX portal.hosts_cluster_idx;
DROP INDEX portal.hosts_metrics_software_state_idx;

CREATE UNIQUE INDEX hosts_name_idx ON portal.hosts (organization, name);
CREATE INDEX hosts_cluster_idx ON portal.hosts (organization, cluster);
CREATE INDEX hosts_metrics_software_state_idx ON portal.hosts (organization, metrics_software_state);

ALTER TABLE portal.alerts ADD COLUMN organization BIGINT DEFAULT NULL;
UPDATE portal.alerts SET organization = (SELECT MIN(id) FROM portal.organizations);
ALTER TABLE portal.alerts ADD FOREIGN KEY (organization) REFERENCES organizations(id);
ALTER TABLE portal.alerts ALTER COLUMN organization SET NOT NULL;

ALTER TABLE portal.expressions ADD COLUMN organization BIGINT DEFAULT NULL;
UPDATE portal.expressions SET organization = (SELECT MIN(id) FROM portal.organizations);
ALTER TABLE portal.expressions ADD FOREIGN KEY (organization) REFERENCES organizations(id);
ALTER TABLE portal.expressions ALTER COLUMN organization SET NOT NULL;

DROP SEQUENCE portal.alerts_etag_seq;
DROP SEQUENCE portal.expressions_etag_seq;

CREATE TABLE portal.alerts_etags (
  id BIGSERIAL PRIMARY KEY,
  organization BIGINT REFERENCES portal.organizations(id),
  etag BIGINT NOT NULL
);

CREATE UNIQUE INDEX alerts_etags_organization on portal.alerts_etags (organization);

CREATE TABLE portal.expressions_etags (
  id BIGSERIAL PRIMARY KEY,
  organization BIGINT REFERENCES portal.organizations(id),
  etag BIGINT NOT NULL
);

CREATE UNIQUE INDEX expressions_etags_organization on portal.expressions_etags (organization);
