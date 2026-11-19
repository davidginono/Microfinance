-- Freeze the selected reporting row on each classification revision.
ALTER TABLE statement_equity_classification
    ADD COLUMN movement_setup_version integer,
    ADD COLUMN movement_row_id uuid,
    ADD COLUMN movement_name varchar(100),
    ADD COLUMN movement_name_sw varchar(100),
    ADD CONSTRAINT statement_equity_movement_setup_fk
        FOREIGN KEY (sacco_id, movement_setup_version) REFERENCES statement_setup_version(sacco_id, version),
    ADD CONSTRAINT statement_equity_movement_row_fields CHECK (
        (movement_setup_version IS NULL AND movement_row_id IS NULL AND movement_name IS NULL AND movement_name_sw IS NULL)
        OR (movement_setup_version IS NOT NULL AND movement_row_id IS NOT NULL AND movement_name IS NOT NULL
            AND length(trim(movement_name)) > 0 AND movement_name_sw IS NOT NULL AND category <> 'UNCLASSIFIED')
    );
