-- New standard loans use their frozen product terms. Earlier loans keep the recorded branch gate.
CREATE OR REPLACE FUNCTION lending_collateral_error(application uuid,institution text,branch text,day date) RETURNS text LANGUAGE plpgsql STABLE AS $$
DECLARE l loan_applications;p loan_product_settings;required boolean;available numeric;coverage_ratio numeric;
BEGIN
 SELECT * INTO l FROM loan_applications WHERE id=application AND sacco_id=institution AND station_id=branch;
 IF l.id IS NULL THEN RETURN 'credit.error.scope';END IF;
 IF l.policy_snapshot->>'collateralPolicySource'='PRODUCT' THEN
   required=(l.policy_snapshot->>'collateralRequired')::boolean;
   coverage_ratio=(l.policy_snapshot->>'collateralCoverageRatio')::numeric;
 ELSE
   SELECT collateral_required INTO required FROM lending_credit_policy
    WHERE sacco_id=institution AND station_id=branch ORDER BY revision DESC LIMIT 1;
   SELECT * INTO p FROM loan_product_settings WHERE id=l.loan_product_setting_id AND sacco_id=institution;
   required=coalesce(required,false) AND coalesce((l.policy_snapshot->>'collateralRequired')::boolean,p.collateral_required,false);
   coverage_ratio=coalesce((l.policy_snapshot->>'collateralCoverageRatio')::numeric,p.min_collateral_coverage_ratio);
 END IF;
 IF NOT coalesce(required,false) THEN RETURN NULL;END IF;
 IF coverage_ratio IS NULL OR coverage_ratio<0 THEN RETURN 'credit.error.history';END IF;
 SELECT coalesce(sum(x.amount),0) INTO available FROM lending_collateral_pledge x JOIN lending_collateral_valuation v ON v.asset_id=x.asset_id
 WHERE x.loan_id=l.id AND x.sacco_id=institution AND x.station_id=branch AND x.kind='PLEDGE' AND v.effective_date<=day AND v.valid_through>=day
  AND v.amount-v.encumbrance >= (SELECT coalesce(sum(y.amount),0) FROM lending_collateral_pledge y WHERE y.asset_id=x.asset_id AND y.kind='PLEDGE');
 IF available<l.amount*coverage_ratio OR available<=0 THEN RETURN 'credit.error.collateralRequired';END IF;RETURN NULL;
END $$;
