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
CREATE TABLE portal.hosts (
    id SERIAL PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    name VARCHAR(255) NOT NULL,
    cluster VARCHAR(255),
    metrics_software_state VARCHAR(255) NOT NULL
);

CREATE UNIQUE INDEX hosts_name_idx ON portal.hosts (name);
CREATE INDEX hosts_cluster_idx ON portal.hosts (cluster);
CREATE INDEX hosts_metrics_software_state_idx ON portal.hosts (metrics_software_state);
