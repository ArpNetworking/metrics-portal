/**
 * Copyright 2017 Smartsheet.com
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

ALTER TABLE portal.alerts ADD COLUMN (notification_group BIGINT);

CREATE TABLE portal.notification_groups (
    id SERIAL PRIMARY KEY,
    uuid UUID,
    name VARCHAR(255) NOT NULL,
    organization BIGINT NOT NULL REFERENCES portal.organizations(id)
);

CREATE TABLE portal.notification_recipients (
    id SERIAL PRIMARY KEY,
    type VARCHAR(64),
    value VARCHAR(255),
    notificationgroup BIGINT NOT NULL REFERENCES portal.notification_groups(id)
);

CREATE UNIQUE INDEX ON portal.notification_groups (organization, name);
CREATE UNIQUE INDEX ON portal.notification_groups (uuid);

ALTER TABLE portal.alerts ADD FOREIGN KEY (notification_group) REFERENCES portal.notification_groups(id);
