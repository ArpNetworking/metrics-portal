-- description: creates hosts table
-- authoredAt: 1525330368000

-- stage: 1
CREATE TABLE hosts
(
    name varchar,
    version bigint,
    created_at timestamp,
    updated_at timestamp,
    cluster varchar,
    metrics_software_state varchar,
    organization uuid,
    PRIMARY KEY (organization, name)
);

-- stage: 2
CREATE MATERIALIZED VIEW hosts_by_organization AS
SELECT *
FROM hosts
WHERE organization IS NOT NULL AND name IS NOT NULL
PRIMARY KEY (organization, name);
