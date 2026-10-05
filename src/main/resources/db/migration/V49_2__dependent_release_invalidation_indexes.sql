-- ISO dates in the trusted immutable typed result preserve chronological order as text.
-- No retained result or approval payload is rewritten.
CREATE INDEX ix_statement_output_scope_through ON statement_output_sets(sacco_id,(result_json::jsonb->>'through'),id);
CREATE INDEX ix_release_scope_output ON accounting_release_requests(sacco_id,statement_output_id,id);
