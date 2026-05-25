alter table if exists sacco_stations
    add column if not exists address_location varchar(255);
