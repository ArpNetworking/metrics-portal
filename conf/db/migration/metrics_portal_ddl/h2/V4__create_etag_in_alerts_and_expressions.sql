-- Alerts etag --
CREATE SEQUENCE portal.alerts_etag_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE;
-- Trigger to be executed after each insert, update and deleteJob statement --
CREATE TRIGGER update_alerts_etag
AFTER INSERT, DELETE, UPDATE ON portal.alerts
FOR EACH ROW
CALL "com.arpnetworking.database.h2.triggers.AlertsUpdateEtagTrigger";

-- Expressions etag --
CREATE SEQUENCE portal.expressions_etag_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE;
-- Trigger to be executed after each insert, update and deleteJob statement --
CREATE TRIGGER update_expressions_etag
AFTER INSERT, DELETE, UPDATE ON portal.expressions
FOR EACH ROW
CALL "com.arpnetworking.database.h2.triggers.ExpressionsUpdateEtagTrigger";
