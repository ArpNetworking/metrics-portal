-- description: creates initial schema
-- authoredAt: 1513974473000
-- up:

-- stage: 1
CREATE TABLE notification_groups
(
    uuid uuid,
    version bigint,
    created_at timestamp,
    updated_at timestamp,
    name varchar,
    organization uuid,
    recipients list<varchar>,
    PRIMARY KEY (uuid, organization)
);

-- stage: 2
CREATE MATERIALIZED VIEW notification_groups_by_organization AS
SELECT *
FROM notification_groups
WHERE organization IS NOT NULL AND uuid IS NOT NULL
PRIMARY KEY (organization, uuid);

-- stage: 3
ALTER TABLE alerts add (
    notification_group_id uuid
);

-- down:

-- stage: 1
ALTER TABLE alerts DROP (notification_group_id);

-- stage: 2
DROP MATERIALIZED VIEW notification_groups_by_organization;

-- stage: 3
DROP TABLE notification_groups;
