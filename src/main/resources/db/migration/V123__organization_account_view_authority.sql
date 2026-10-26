-- Existing mandates acquire no viewing power. A verified new revision is required.
ALTER TABLE lending_group_authority_signer ADD COLUMN may_view_account boolean NOT NULL DEFAULT false;
ALTER TABLE lending_group_authority_signer DROP CONSTRAINT lending_group_authority_signer_check;
ALTER TABLE lending_group_authority_signer ADD CONSTRAINT lending_group_authority_signer_powers
 CHECK(may_apply OR may_sign OR may_receive OR may_view_account);
-- The existing immutable-history and deferred completeness guards remain in force.
CREATE INDEX ix_group_authority_account_viewer ON lending_group_authority_signer(client_id,authority_id) WHERE may_view_account;
