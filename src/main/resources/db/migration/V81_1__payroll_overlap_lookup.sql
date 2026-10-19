-- Resolve loan/month overlap without scanning historical employer schedules.
CREATE INDEX payroll_batch_line_loan ON payroll_batch_line(loan_id,batch_id) WHERE amount>0;
