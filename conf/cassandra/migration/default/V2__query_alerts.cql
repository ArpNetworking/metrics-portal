-- description: creates initial schema
-- authoredAt: 1370028268000
-- up:

-- stage: 1
DROP MATERIALIZED VIEW alerts_by_cluster;

-- stage: 2
DROP MATERIALIZED VIEW alerts_by_organization;

-- stage: 3
ALTER TABLE alerts DROP (cluster, service, context, metric, statistic, operator, quantity_value, quantity_unit);

-- stage: 4
ALTER TABLE alerts ADD (query varchar);

-- stage: 5
CREATE MATERIALIZED VIEW alerts_by_organization AS
SELECT *
FROM alerts
WHERE organization IS NOT NULL AND uuid IS NOT NULL
PRIMARY KEY (organization, uuid);

-- down:

--stage: 1
DROP MATERIALIZED VIEW alerts_by_organization;

-- stage: 2
ALTER TABLE alerts DROP (query);

-- stage: 3
ALTER TABLE alerts add (
    cluster varchar,
    service varchar,
    context varchar,
    metric varchar,
    statistic varchar,
    operator varchar,
    quantity_value double,
    quantity_unit varchar
    );

-- stage: 4
CREATE MATERIALIZED VIEW alerts_by_organization AS
SELECT *
FROM alerts
WHERE organization IS NOT NULL AND uuid IS NOT NULL
PRIMARY KEY (organization, uuid);

-- stage: 5
CREATE MATERIALIZED VIEW alerts_by_cluster AS
SELECT organization, cluster
FROM alerts
WHERE organization IS NOT NULL AND uuid IS NOT NULL AND cluster IS NOT NULL
PRIMARY KEY (cluster, organization, uuid);
