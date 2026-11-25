-- A journal contributes one document number, even when a receipt covers several journals.
CREATE VIEW accounting_posted_voucher_document AS
SELECT j.id AS journal_id, j.sacco_id, j.station_id,
       coalesce(v.number, r.number, g.number, s.number) AS number,
       coalesce(v.type, CASE WHEN r.id IS NOT NULL OR g.id IS NOT NULL THEN 'RECEIPT' END, s.type) AS type,
       coalesce(v.posted_at, r.posted_at, g.posted_at, s.issued_at) AS issued_at
FROM gl_journal j
LEFT JOIN accounting_voucher v ON (v.journal_id, v.sacco_id, v.station_id) = (j.id, j.sacco_id, j.station_id)
LEFT JOIN accountant_loan_post p ON (p.journal_id, p.sacco_id, p.station_id) = (j.id, j.sacco_id, j.station_id) AND p.state = 'POSTED'
LEFT JOIN accountant_loan_receipt_row x ON (x.posting_id, x.sacco_id, x.station_id) = (p.id, p.sacco_id, p.station_id)
LEFT JOIN accountant_loan_receipt r ON (r.id, r.sacco_id, r.station_id) = (x.receipt_id, x.sacco_id, x.station_id)
LEFT JOIN lending_group_receipt_loan y ON (y.posting_id, y.sacco_id, y.station_id) = (p.id, p.sacco_id, p.station_id)
LEFT JOIN lending_group_receipt g ON (g.id, g.sacco_id, g.station_id) = (y.receipt_id, y.sacco_id, y.station_id)
LEFT JOIN accounting_system_voucher s ON (s.journal_id, s.sacco_id, s.station_id) = (j.id, j.sacco_id, j.station_id)
WHERE j.state = 'POSTED' AND coalesce(v.number, r.number, g.number, s.number) IS NOT NULL;
