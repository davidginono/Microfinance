-- New codes are tenant-scoped; existing identities and references remain unchanged.
CREATE TABLE gl_library_code_counter (
 sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),
 kind varchar(11) NOT NULL CHECK(kind IN ('ACTIVITY','TRANSACTION')),
 last_number bigint NOT NULL CHECK(last_number>0), PRIMARY KEY(sacco_id,kind)
);
INSERT INTO gl_library_code_counter
 SELECT sacco_id,'ACTIVITY',max(split_part(code,'-',2)::bigint) FROM gl_activity WHERE code ~ '^ACT-[0-9]{1,18}$' GROUP BY sacco_id HAVING max(split_part(code,'-',2)::bigint)>0;
INSERT INTO gl_library_code_counter
 SELECT sacco_id,'TRANSACTION',max(split_part(code,'-',2)::bigint) FROM gl_transaction_code WHERE code ~ '^TXN-[0-9]{1,18}$' GROUP BY sacco_id HAVING max(split_part(code,'-',2)::bigint)>0;
ALTER TABLE gl_activity ADD COLUMN creation_key uuid, ADD COLUMN creation_hash varchar(64), ADD UNIQUE(sacco_id,creation_key);
ALTER TABLE gl_transaction_code ADD COLUMN creation_key uuid, ADD COLUMN creation_hash varchar(64), ADD UNIQUE(sacco_id,creation_key);
