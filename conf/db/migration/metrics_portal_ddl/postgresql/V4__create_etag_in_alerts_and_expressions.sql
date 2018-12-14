-- Alerts etag --
CREATE SEQUENCE portal.alerts_etag_seq;

CREATE OR REPLACE FUNCTION update_alerts_etag() RETURNS TRIGGER AS $update_alerts_etag$
BEGIN
    PERFORM NEXTVAL('portal.alerts_etag_seq');
    RETURN NEW;
END;
$update_alerts_etag$ LANGUAGE 'plpgsql';
-- Trigger to be executed after each insert, update and deleteJob statement --
CREATE TRIGGER update_alerts_etag
BEFORE INSERT OR DELETE OR UPDATE ON portal.alerts
FOR EACH ROW
EXECUTE PROCEDURE update_alerts_etag();

-- Expressions etag --
CREATE SEQUENCE portal.expressions_etag_seq;

CREATE OR REPLACE FUNCTION update_expressions_etag() RETURNS TRIGGER AS $update_expressions_etag$
BEGIN
    PERFORM NEXTVAL('portal.expressions_etag_seq');
    RETURN NEW;
END;
$update_expressions_etag$ LANGUAGE 'plpgsql';
-- Trigger to be executed after each insert, update and deleteJob statement --
CREATE TRIGGER update_expressions_etag
BEFORE INSERT OR DELETE OR UPDATE ON portal.expressions
FOR EACH ROW
EXECUTE PROCEDURE update_expressions_etag();
