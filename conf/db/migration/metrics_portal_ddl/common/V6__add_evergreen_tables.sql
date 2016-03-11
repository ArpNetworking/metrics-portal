/**
 * Copyright 2016 Groupon
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

CREATE TABLE portal.package_versions (
    id SERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    name VARCHAR(255) NOT NULL,
    version VARCHAR(255) NOT NULL,
    uri VARCHAR(2047) NOT NULL,
    UNIQUE (name, version, uri)
);

CREATE TABLE portal.version_sets (
    id SERIAL PRIMARY KEY,
    uuid UUID NOT NULL,
    version SERIAL NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

/* Many-to-many join table between version_sets and package_versions */
CREATE TABLE version_set_package_versions (
    id SERIAL PRIMARY KEY,
    version_set INTEGER REFERENCES portal.version_sets,
    package_version INTEGER REFERENCES portal.package_versions,
    UNIQUE (version_set, package_version)
);

CREATE TABLE portal.version_specifications (
    id SERIAL PRIMARY KEY,
    uuid UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    precedence INTEGER NOT NULL UNIQUE,
    version_set_id INTEGER REFERENCES portal.version_sets
);
