package com.sacco.mvp.service.dto;

import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.EarlyPayoffInterest;
import com.sacco.mvp.domain.InterestAccrualMode;
import lombok.Data;

@Data
public class LoanProductRepaymentForm {
    private boolean topUpSettingsPresent;
    private boolean topUpAllowed;
    private boolean topUpFeesEnabled;
    private boolean topUpApplicationFee;
    private boolean topUpInsuranceFee;
    private boolean topUpProcessingFee;
    private LoanProductSetting.TopUpPercentageBasis topUpPercentageBasis;
    private EarlyPayoffInterest earlyPayoffInterest;
    private InterestAccrualMode interestAccrualMode;
}
