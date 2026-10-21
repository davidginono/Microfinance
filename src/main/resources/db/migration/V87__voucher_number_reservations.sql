-- Reserve the voucher number shown on a create form so it remains stable through direct or configured posting.
CREATE TABLE accounting_voucher_number_reservation (
    request_key uuid PRIMARY KEY,
    sacco_id varchar(255) NOT NULL REFERENCES registered_saccos(sacco_id),
    station_id varchar(255) NOT NULL,
    voucher_type varchar(10) NOT NULL CHECK (voucher_type IN ('RECEIPT','PAYMENT','JOURNAL')),
    effective_date date NOT NULL,
    voucher_number varchar(40) NOT NULL UNIQUE,
    reserved_at timestamptz NOT NULL,
    consumed_at timestamptz
);
CREATE INDEX ix_voucher_number_reservation_scope
    ON accounting_voucher_number_reservation(sacco_id,station_id,reserved_at DESC);
