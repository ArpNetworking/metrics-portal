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

/* Update all pkey id columns to be bigserial */
ALTER TABLE portal.alerts ALTER COLUMN id TYPE BIGSERIAL;
ALTER TABLE portal.expressions ALTER COLUMN id TYPE BIGSERIAL;
ALTER TABLE portal.hosts ALTER COLUMN id TYPE BIGSERIAL;
ALTER TABLE portal.nagios_extensions ALTER COLUMN id TYPE BIGSERIAL;
ALTER TABLE portal.package_versions ALTER COLUMN id TYPE BIGSERIAL;
ALTER TABLE portal.version_set_package_versions ALTER COLUMN id TYPE BIGSERIAL;
ALTER TABLE portal.version_sets ALTER COLUMN id TYPE BIGSERIAL;
ALTER TABLE portal.version_specifications ALTER COLUMN id TYPE BIGSERIAL;
ALTER TABLE portal.version_specification_attributes ALTER COLUMN id TYPE BIGSERIAL;
