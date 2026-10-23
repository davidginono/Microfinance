-- Stable revision ordering even when two configurations share the same timestamp.
ALTER TABLE lending_group_policy ADD COLUMN revision bigint GENERATED ALWAYS AS IDENTITY;
CREATE UNIQUE INDEX ux_lending_group_policy_revision ON lending_group_policy(revision);
CREATE INDEX ix_lending_group_policy_current ON lending_group_policy(sacco_id,station_id,product_id,model,revision DESC);
