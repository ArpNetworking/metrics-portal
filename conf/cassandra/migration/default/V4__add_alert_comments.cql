-- description: adds comment field to alert
-- authoredAt: 1516916489000
-- up:

-- stage: 1
DROP MATERIALIZED VIEW alerts_by_organization;

-- stage: 2
ALTER TABLE alerts ADD (comment varchar);

-- stage: 3
CREATE MATERIALIZED VIEW alerts_by_organization AS
SELECT *
FROM alerts
WHERE organization IS NOT NULL AND uuid IS NOT NULL
PRIMARY KEY (organization, uuid);

-- down:

--stage: 1
DROP MATERIALIZED VIEW alerts_by_organization;

-- stage: 2
ALTER TABLE alerts DROP (comment);

-- stage: 3
CREATE MATERIALIZED VIEW alerts_by_organization AS
SELECT *
FROM alerts
WHERE organization IS NOT NULL AND uuid IS NOT NULL
PRIMARY KEY (organization, uuid);
