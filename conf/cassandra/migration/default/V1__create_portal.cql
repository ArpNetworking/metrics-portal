-- description: creates initial schema
-- authoredAt: 1370028263000
-- up:

-- stage: 1
CREATE TABLE alerts
(
    uuid uuid,
    version bigint,
    created_at timestamp,
    updated_at timestamp,
    name varchar,
    cluster varchar,
    service varchar,
    context varchar,
    metric varchar,
    statistic varchar,
    period_in_seconds int,
    operator varchar,
    quantity_value double,
    quantity_unit varchar,
    organization uuid,
    nagios_extensions map<varchar, varchar>,
    PRIMARY KEY (uuid, organization)
);

-- stage: 2
CREATE MATERIALIZED VIEW alerts_by_cluster AS
SELECT organization, cluster
FROM alerts
WHERE organization IS NOT NULL AND uuid IS NOT NULL AND cluster IS NOT NULL
PRIMARY KEY (cluster, organization, uuid);

-- stage: 3
CREATE MATERIALIZED VIEW alerts_by_organization AS
SELECT *
FROM alerts
WHERE organization IS NOT NULL AND uuid IS NOT NULL
PRIMARY KEY (organization, uuid);

-- down:

-- stage: 1
DROP MATERIALIZED VIEW alerts_by_organization;

-- stage: 2
DROP MATERIALIZED VIEW alerts_by_cluster;

-- stage: 3
DROP TABLE alerts;
