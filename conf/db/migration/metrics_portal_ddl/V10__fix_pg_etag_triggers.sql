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

CREATE OR REPLACE FUNCTION update_alerts_etag() RETURNS TRIGGER AS $update_alerts_etag$
DECLARE
  pl_organization integer;
  pl_modified integer;
BEGIN
  IF TG_OP = 'INSERT' OR TG_OP = 'UPDATE' THEN
    pl_organization = NEW.organization;
  ELSE
    pl_organization = OLD.organization;
  END IF;
  UPDATE portal.alerts_etags SET etag = etag + 1 WHERE organization = pl_organization;
  GET DIAGNOSTICS pl_modified = ROW_COUNT;
  IF pl_modified = 0 THEN
    INSERT INTO portal.alerts_etags (organization, etag) VALUES (pl_organization, 1);
  END IF;
  IF TG_OP = 'INSERT' OR TG_OP = 'UPDATE' THEN
    RETURN NEW;
  ELSE
    RETURN OLD;
  END IF;
END;
$update_alerts_etag$ LANGUAGE 'plpgsql';

CREATE OR REPLACE FUNCTION update_expressions_etag() RETURNS TRIGGER AS $update_expressions_etag$
DECLARE
  pl_organization integer;
  pl_modified integer;
BEGIN
  IF TG_OP = 'INSERT' OR TG_OP = 'UPDATE' THEN
    pl_organization = NEW.organization;
  ELSE
    pl_organization = OLD.organization;
  END IF;
  UPDATE portal.expressions_etags SET etag = etag + 1 WHERE organization = pl_organization;
  GET DIAGNOSTICS pl_modified = ROW_COUNT;
  IF pl_modified = 0 THEN
    INSERT INTO portal.expressions_etags (organization, etag) VALUES (pl_organization, 1);
  END IF;
  IF TG_OP = 'INSERT' OR TG_OP = 'UPDATE' THEN
    RETURN NEW;
  ELSE
    RETURN OLD;
  END IF;
END;
$update_expressions_etag$ LANGUAGE 'plpgsql';

