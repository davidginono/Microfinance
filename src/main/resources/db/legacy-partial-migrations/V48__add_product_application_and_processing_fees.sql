alter table loan_product_settings
    add column if not exists application_fee numeric(18, 2),
    add column if not exists processing_fee_rate numeric(6, 4);

update loan_product_settings product
set application_fee = coalesce(settings.application_fee, 15000.00)
from sacco_settings settings
where product.sacco_id = settings.sacco_id
  and product.application_fee is null;

update loan_product_settings
set application_fee = 15000.00
where application_fee is null;

update loan_product_settings
set processing_fee_rate = 0.0000
where processing_fee_rate is null;
